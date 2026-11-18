package com.swiftdrop.backend.modules.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.backend.modules.messaging.config.MessagingProperties;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.messaging.event.ParcelStatusChangedPayload;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.webhook.client.WebhookHttpClient;
import com.swiftdrop.backend.modules.webhook.client.WebhookHttpResult;
import com.swiftdrop.backend.modules.webhook.endpoint.MerchantWebhookDirectory;
import com.swiftdrop.backend.modules.webhook.endpoint.WebhookEndpoint;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.backend.modules.webhook.repository.WebhookDeliveryRepository;
import com.swiftdrop.backend.modules.webhook.signing.WebhookSigner;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The webhook retry test: failures are retried with the configured delays, success
 * stops the retries, and running out of attempts marks the delivery dead.
 */
@ExtendWith(MockitoExtension.class)
class WebhookDispatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T09:30:00Z");
    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final String URL = "https://shop.example.lk/hooks/swiftdrop";
    private static final String SECRET = "whsec_merchant";
    private static final List<Duration> DELAYS = List.of(Duration.ofSeconds(10), Duration.ofSeconds(30));

    @Mock
    private WebhookDeliveryRepository repository;
    @Mock
    private MerchantWebhookDirectory directory;
    @Mock
    private WebhookHttpClient httpClient;
    @Mock
    private WebhookRetryScheduler retryScheduler;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final WebhookSigner signer = new WebhookSigner();
    /** Stands in for the database: the latest saved copy of each delivery. */
    private final Map<UUID, WebhookDelivery> saved = new HashMap<>();
    private WebhookDispatchService service;

    @BeforeEach
    void setUp() {
        RetryPolicy retryPolicy = new RetryPolicy(new MessagingProperties(
                new MessagingProperties.Outbox(true, 50, Duration.ofSeconds(5), Duration.ofMinutes(5)),
                new MessagingProperties.Webhook(DELAYS, Duration.ofSeconds(3), Duration.ofSeconds(5))));
        service = new WebhookDispatchService(repository, directory, httpClient, signer, retryPolicy, retryScheduler,
                jsonMapper, Clock.fixed(NOW, ZoneOffset.UTC));

        lenient().when(directory.findMerchantIdForParcel(PARCEL_ID)).thenReturn(Optional.of(MERCHANT_ID));
        lenient().when(directory.findEndpoint(MERCHANT_ID))
                .thenReturn(Optional.of(new WebhookEndpoint(MERCHANT_ID, URL, SECRET)));
        lenient().when(repository.save(any(WebhookDelivery.class))).thenAnswer(invocation -> {
            WebhookDelivery delivery = invocation.getArgument(0);
            saved.put(delivery.getId(), delivery);
            return delivery;
        });
        lenient().when(repository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get(invocation.<UUID>getArgument(0))));
    }

    @Test
    void successfulFirstAttemptIsDeliveredAndNotRetried() {
        answerWith(WebhookHttpResult.response(200));

        AttemptOutcome outcome = service.handleEvent(statusEvent());

        WebhookDelivery delivery = onlyDelivery();
        assertThat(outcome).isEqualTo(AttemptOutcome.DELIVERED);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getLastResponseCode()).isEqualTo(200);
        assertThat(delivery.getDeliveredAt()).isEqualTo(NOW);
        verifyNoInteractions(retryScheduler);
    }

    @Test
    void requestIsSignedAndCarriesOnlyMerchantSafeFields() {
        answerWith(WebhookHttpResult.response(204));

        service.handleEvent(statusEvent());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).post(eq(URL), body.capture(), headers.capture());

        assertThat(signer.verify(SECRET, headers.getValue().get(WebhookSigner.SIGNATURE_HEADER), body.getValue(),
                NOW, Duration.ofMinutes(5))).isTrue();
        assertThat(headers.getValue())
                .containsEntry("Content-Type", "application/json")
                .containsEntry(WebhookDispatchService.EVENT_HEADER, "parcel.status_changed")
                .containsEntry(WebhookDispatchService.ATTEMPT_HEADER, "1");

        JsonNode json = jsonMapper.readTree(body.getValue());
        assertThat(json.get("type").asString()).isEqualTo("parcel.status_changed");
        assertThat(json.get("data").get("trackingNumber").asString()).isEqualTo("SDP7845621");
        assertThat(json.get("data").get("status").asString()).isEqualTo("IN_TRANSIT");
        assertThat(json.get("data").get("previousStatus").asString()).isEqualTo("PICKED_UP");
        assertThat(body.getValue()).doesNotContain(PARCEL_ID.toString());
    }

    @Test
    void failedAttemptSchedulesRetryWithFirstDelay() {
        answerWith(WebhookHttpResult.response(500));

        AttemptOutcome outcome = service.handleEvent(statusEvent());

        WebhookDelivery delivery = onlyDelivery();
        assertThat(outcome).isEqualTo(AttemptOutcome.RETRY_SCHEDULED);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.RETRYING);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getLastResponseCode()).isEqualTo(500);
        assertThat(delivery.getLastError()).isEqualTo("Endpoint answered HTTP 500");
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
        verify(retryScheduler).schedule(delivery.getId(), Duration.ofSeconds(10));
    }

    @Test
    void retriesWithIncreasingDelaysThenGivesUp() {
        answerWith(WebhookHttpResult.noResponse("Endpoint did not answer within 5s"));

        assertThat(service.handleEvent(statusEvent())).isEqualTo(AttemptOutcome.RETRY_SCHEDULED);
        UUID id = onlyDelivery().getId();
        assertThat(service.retry(id)).isEqualTo(AttemptOutcome.RETRY_SCHEDULED);
        assertThat(service.retry(id)).isEqualTo(AttemptOutcome.DEAD);

        WebhookDelivery delivery = saved.get(id);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DEAD);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
        assertThat(delivery.getMaxAttempts()).isEqualTo(3);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getLastResponseCode()).isNull();

        InOrder order = inOrder(retryScheduler);
        order.verify(retryScheduler).schedule(id, Duration.ofSeconds(10));
        order.verify(retryScheduler).schedule(id, Duration.ofSeconds(30));
        order.verifyNoMoreInteractions();
    }

    @Test
    void retryThatSucceedsStopsFurtherRetries() {
        when(httpClient.post(anyString(), anyString(), anyMap()))
                .thenReturn(WebhookHttpResult.response(503))
                .thenReturn(WebhookHttpResult.response(200));

        service.handleEvent(statusEvent());
        UUID id = onlyDelivery().getId();
        AttemptOutcome outcome = service.retry(id);

        assertThat(outcome).isEqualTo(AttemptOutcome.DELIVERED);
        assertThat(saved.get(id).getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(saved.get(id).getAttemptCount()).isEqualTo(2);
        assertThat(saved.get(id).getLastError()).isNull();
        verify(retryScheduler).schedule(id, Duration.ofSeconds(10));
    }

    @Test
    void eachAttemptIsNumberedInItsHeader() {
        answerWith(WebhookHttpResult.response(500));

        service.handleEvent(statusEvent());
        service.retry(onlyDelivery().getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(httpClient, times(2)).post(eq(URL), anyString(), headers.capture());
        assertThat(headers.getAllValues()).extracting(h -> h.get(WebhookDispatchService.ATTEMPT_HEADER))
                .containsExactly("1", "2");
    }

    @Test
    void endpointRemovedBeforeRetryMarksDeliveryDead() {
        answerWith(WebhookHttpResult.response(500));
        service.handleEvent(statusEvent());
        UUID id = onlyDelivery().getId();
        when(directory.findEndpoint(MERCHANT_ID)).thenReturn(Optional.empty());

        AttemptOutcome outcome = service.retry(id);

        assertThat(outcome).isEqualTo(AttemptOutcome.DEAD);
        assertThat(saved.get(id).getStatus()).isEqualTo(WebhookDeliveryStatus.DEAD);
        assertThat(saved.get(id).getLastError()).isEqualTo("Merchant no longer has a webhook endpoint");
    }

    @Test
    void retryOfFinishedOrUnknownDeliveryDoesNothing() {
        answerWith(WebhookHttpResult.response(200));
        service.handleEvent(statusEvent());

        assertThat(service.retry(onlyDelivery().getId())).isEqualTo(AttemptOutcome.SKIPPED);
        assertThat(service.retry(UUID.randomUUID())).isEqualTo(AttemptOutcome.SKIPPED);
        verify(httpClient, times(1)).post(anyString(), anyString(), anyMap());
    }

    @Test
    void manualRetryGivesDeadDeliveryOneMoreAttempt() {
        answerWith(WebhookHttpResult.response(500));
        service.handleEvent(statusEvent());
        UUID id = onlyDelivery().getId();
        service.retry(id);
        service.retry(id);
        assertThat(saved.get(id).getStatus()).isEqualTo(WebhookDeliveryStatus.DEAD);

        answerWith(WebhookHttpResult.response(200));
        WebhookDelivery result = service.retryNow(saved.get(id));

        assertThat(result.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(result.getAttemptCount()).isEqualTo(4);
    }

    @Test
    void failedManualRetryIsDeadAgainWithoutAutomaticRetries() {
        answerWith(WebhookHttpResult.response(500));
        service.handleEvent(statusEvent());
        UUID id = onlyDelivery().getId();
        service.retry(id);
        service.retry(id);

        WebhookDelivery result = service.retryNow(saved.get(id));

        assertThat(result.getStatus()).isEqualTo(WebhookDeliveryStatus.DEAD);
        assertThat(result.getAttemptCount()).isEqualTo(4);
        verify(retryScheduler, times(2)).schedule(eq(id), any());
    }

    @Test
    void customerParcelWithoutMerchantSendsNothing() {
        UUID customerParcel = UUID.randomUUID();
        when(directory.findMerchantIdForParcel(customerParcel)).thenReturn(Optional.empty());

        AttemptOutcome outcome = service.handleEvent(statusEvent(UUID.randomUUID(), customerParcel));

        assertThat(outcome).isEqualTo(AttemptOutcome.SKIPPED);
        verify(repository, never()).save(any());
        verifyNoInteractions(httpClient);
    }

    @Test
    void merchantWithoutEndpointSendsNothing() {
        when(directory.findEndpoint(MERCHANT_ID)).thenReturn(Optional.empty());

        assertThat(service.handleEvent(statusEvent())).isEqualTo(AttemptOutcome.SKIPPED);
        verifyNoInteractions(httpClient);
    }

    @Test
    void duplicateEventIsIgnored() {
        DomainEventMessage event = statusEvent();
        when(repository.existsByEventIdAndMerchantId(event.eventId(), MERCHANT_ID)).thenReturn(true);

        assertThat(service.handleEvent(event)).isEqualTo(AttemptOutcome.SKIPPED);
        verifyNoInteractions(httpClient);
    }

    @Test
    void otherEventTypesAreIgnored() {
        DomainEventMessage event = new DomainEventMessage(UUID.randomUUID(), "merchant.approved", "merchant",
                MERCHANT_ID, NOW, jsonMapper.createObjectNode());

        assertThat(service.handleEvent(event)).isEqualTo(AttemptOutcome.SKIPPED);
        verifyNoInteractions(directory, httpClient);
    }

    private void answerWith(WebhookHttpResult result) {
        when(httpClient.post(anyString(), anyString(), anyMap())).thenReturn(result);
    }

    private WebhookDelivery onlyDelivery() {
        assertThat(saved).hasSize(1);
        return saved.values().iterator().next();
    }

    private DomainEventMessage statusEvent() {
        return statusEvent(UUID.randomUUID(), PARCEL_ID);
    }

    private DomainEventMessage statusEvent(UUID eventId, UUID parcelId) {
        ParcelStatusChangedPayload payload = new ParcelStatusChangedPayload(parcelId, "SDP7845621",
                ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, null, NOW);
        return new DomainEventMessage(eventId, "parcel.status_changed", "parcel", parcelId, NOW,
                jsonMapper.valueToTree(payload));
    }
}
