//Processes the events and handles sending webhook notifications to merchants.


package com.swiftdrop.backend.modules.webhook.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.messaging.event.ParcelEvents;
import com.swiftdrop.backend.modules.messaging.event.ParcelStatusChangedPayload;
import com.swiftdrop.backend.modules.webhook.client.WebhookHttpClient;
import com.swiftdrop.backend.modules.webhook.client.WebhookHttpResult;
import com.swiftdrop.backend.modules.webhook.dto.WebhookPayload;
import com.swiftdrop.backend.modules.webhook.endpoint.MerchantWebhookDirectory;
import com.swiftdrop.backend.modules.webhook.endpoint.WebhookEndpoint;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery;
import com.swiftdrop.backend.modules.webhook.repository.WebhookDeliveryRepository;
import com.swiftdrop.backend.modules.webhook.signing.WebhookSigner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns events into signed HTTP calls to merchants, and decides after each attempt
 * whether to retry, give up, or stop because it worked.
 *
 * <p>No database transaction is held open during the HTTP call: the delivery is saved
 * before and after it, and the optimistic lock on {@link WebhookDelivery} stops two
 * attempts from overwriting each other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatchService {

    public static final String EVENT_HEADER = "X-SwiftDrop-Event";
    public static final String EVENT_ID_HEADER = "X-SwiftDrop-Event-Id";
    public static final String DELIVERY_HEADER = "X-SwiftDrop-Delivery";
    public static final String ATTEMPT_HEADER = "X-SwiftDrop-Attempt";
    static final String USER_AGENT = "SwiftDrop-Webhooks/1.0";

    private final WebhookDeliveryRepository repository;
    private final MerchantWebhookDirectory directory;
    private final WebhookHttpClient httpClient;
    private final WebhookSigner signer;
    private final RetryPolicy retryPolicy;
    private final WebhookRetryScheduler retryScheduler;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * Handles an event from RabbitMQ: works out which merchant (if any) should hear about
     * it, records a delivery and makes the first attempt.
     */
    public AttemptOutcome handleEvent(DomainEventMessage event) {
        if (!ParcelEvents.STATUS_CHANGED.equals(event.eventType())) {
            return AttemptOutcome.SKIPPED;
        }
        Optional<UUID> merchantId = directory.findMerchantIdForParcel(event.aggregateId());
        if (merchantId.isEmpty()) {
            return AttemptOutcome.SKIPPED;
        }
        Optional<WebhookEndpoint> endpoint = directory.findEndpoint(merchantId.get());
        if (endpoint.isEmpty()) {
            log.debug("Merchant {} has no webhook endpoint; event {} not sent", merchantId.get(), event.eventId());
            return AttemptOutcome.SKIPPED;
        }
        if (repository.existsByEventIdAndMerchantId(event.eventId(), merchantId.get())) {
            log.debug("Event {} already delivered to merchant {}; ignoring duplicate", event.eventId(), merchantId.get());
            return AttemptOutcome.SKIPPED;
        }

        ParcelStatusChangedPayload parcelEvent = jsonMapper.treeToValue(event.payload(), ParcelStatusChangedPayload.class);
        String body = jsonMapper.writeValueAsString(WebhookPayload.parcelStatusChanged(
                event.eventId(), event.eventType(), event.occurredAt(), parcelEvent));

        WebhookDelivery delivery;
        try {
            delivery = repository.save(WebhookDelivery.create(event.eventId(), merchantId.get(),
                    parcelEvent.parcelId(), parcelEvent.trackingNumber(), event.eventType(),
                    endpoint.get().url(), body, retryPolicy.maxAttempts()));
        } catch (DataIntegrityViolationException ex) {
            // Another consumer recorded the same event at the same moment.
            return AttemptOutcome.SKIPPED;
        }
        return attempt(delivery, endpoint.get());
    }

    /**
     * Makes the next attempt for a delivery whose retry delay has passed.
     */
    public AttemptOutcome retry(UUID deliveryId) {
        Optional<WebhookDelivery> found = repository.findById(deliveryId);
        if (found.isEmpty()) {
            log.warn("Retry for unknown webhook delivery {} ignored", deliveryId);
            return AttemptOutcome.SKIPPED;
        }
        WebhookDelivery delivery = found.get();
        if (delivery.isFinished()) {
            return AttemptOutcome.SKIPPED;
        }
        return attemptWithCurrentEndpoint(delivery);
    }

    /**
     * Gives a dead delivery one more attempt straight away (used by the admin and merchant API).
     *
     * @return the delivery after the attempt
     */
    public WebhookDelivery retryNow(WebhookDelivery delivery) {
        delivery.reopenForManualRetry();
        WebhookDelivery reopened = repository.save(delivery);
        attemptWithCurrentEndpoint(reopened);
        return repository.findById(reopened.getId()).orElse(reopened);
    }

    private AttemptOutcome attemptWithCurrentEndpoint(WebhookDelivery delivery) {
        // Look the endpoint up again: the merchant may have changed their URL or secret.
        Optional<WebhookEndpoint> endpoint = directory.findEndpoint(delivery.getMerchantId());
        if (endpoint.isEmpty()) {
            delivery.recordFailure(delivery.getEndpointUrl(), null,
                    "Merchant no longer has a webhook endpoint", clock.instant());
            delivery.markDead();
            repository.save(delivery);
            return AttemptOutcome.DEAD;
        }
        return attempt(delivery, endpoint.get());
    }

    private AttemptOutcome attempt(WebhookDelivery delivery, WebhookEndpoint endpoint) {
        int attemptNumber = delivery.getAttemptCount() + 1;
        WebhookHttpResult result = httpClient.post(endpoint.url(), delivery.getPayload(),
                headers(delivery, endpoint, attemptNumber));
        Instant now = clock.instant();

        if (result.isSuccess()) {
            delivery.recordSuccess(endpoint.url(), result.statusCode(), now);
            repository.save(delivery);
            log.info("Webhook {} delivered to merchant {} on attempt {}",
                    delivery.getId(), delivery.getMerchantId(), attemptNumber);
            return AttemptOutcome.DELIVERED;
        }

        delivery.recordFailure(endpoint.url(), result.statusCode(), result.error(), now);
        if (delivery.hasAttemptsLeft()) {
            Duration delay = retryPolicy.delayAfter(delivery.getAttemptCount());
            delivery.scheduleRetry(now.plus(delay));
            WebhookDelivery saved = repository.save(delivery);
            retryScheduler.schedule(saved.getId(), delay);
            log.info("Webhook {} attempt {} failed ({}); retrying in {}s",
                    delivery.getId(), attemptNumber, result.error(), delay.toSeconds());
            return AttemptOutcome.RETRY_SCHEDULED;
        }

        delivery.markDead();
        repository.save(delivery);
        log.warn("Webhook {} to merchant {} failed after {} attempts ({}); moved to dead-letter queue",
                delivery.getId(), delivery.getMerchantId(), attemptNumber, result.error());
        return AttemptOutcome.DEAD;
    }

    private Map<String, String> headers(WebhookDelivery delivery, WebhookEndpoint endpoint, int attemptNumber) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        headers.put("User-Agent", USER_AGENT);
        headers.put(EVENT_HEADER, delivery.getEventType());
        headers.put(EVENT_ID_HEADER, delivery.getEventId().toString());
        headers.put(DELIVERY_HEADER, delivery.getId().toString());
        headers.put(ATTEMPT_HEADER, String.valueOf(attemptNumber));
        headers.put(WebhookSigner.SIGNATURE_HEADER,
                signer.sign(endpoint.secret(), clock.instant().getEpochSecond(), delivery.getPayload()));
        return headers;
    }
}
