package com.swiftdrop.backend.modules.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.backend.modules.courier.repository.DeliveryProofRepository;
import com.swiftdrop.backend.common.config.ClockConfig;
import com.swiftdrop.backend.modules.messaging.config.MessagingTopology;
import com.swiftdrop.backend.modules.messaging.config.RabbitMqConfig;
import com.swiftdrop.backend.modules.messaging.entity.OutboxEvent;
import com.swiftdrop.backend.modules.messaging.entity.OutboxStatus;
import com.swiftdrop.backend.modules.messaging.listener.OutboxEventRecorder;
import com.swiftdrop.backend.modules.messaging.repository.OutboxEventRepository;
import com.swiftdrop.backend.modules.messaging.service.OutboxPublisher;
import com.swiftdrop.backend.modules.messaging.service.OutboxService;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.entity.ParcelStatusHistory;
import com.swiftdrop.backend.modules.tracking.exception.InvalidStatusTransitionException;
import com.swiftdrop.backend.modules.tracking.repository.ParcelStatusHistoryRepository;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.modules.webhook.client.WebhookHttpClient;
import com.swiftdrop.backend.modules.webhook.consumer.WebhookConsumer;
import com.swiftdrop.backend.modules.webhook.endpoint.MerchantWebhookDirectory;
import com.swiftdrop.backend.modules.webhook.endpoint.WebhookEndpoint;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.backend.modules.webhook.repository.WebhookDeliveryRepository;
import com.swiftdrop.backend.modules.webhook.service.RetryPolicy;
import com.swiftdrop.backend.modules.webhook.service.WebhookDispatchService;
import com.swiftdrop.backend.modules.webhook.service.WebhookRetryScheduler;
import com.swiftdrop.backend.modules.webhook.signing.WebhookSigner;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The event-flow integration test. Everything is real except the merchant: a courier
 * status update goes through the outbox, a real RabbitMQ broker (Testcontainers) and the
 * webhook consumer, and ends as a signed HTTP POST to a local fake merchant server.
 *
 * <p>Loads only the parcel, messaging and webhook beans (not the whole application), and
 * is skipped automatically when Docker is not available.
 */
@SpringBootTest(
        classes = EventFlowIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.rabbitmq.publisher-confirm-type=simple",
                // The shared test properties keep listeners stopped; this test needs them running.
                "spring.rabbitmq.listener.simple.auto-startup=true",
                "app.messaging.outbox.poll-interval-ms=100",
                "app.messaging.webhook.retry-delays=300ms,600ms",
                "app.messaging.webhook.request-timeout=2s"
        })
@Testcontainers(disabledWithoutDocker = true)
class EventFlowIntegrationTest {

    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final String SECRET = "whsec_integration_test";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.4.0-management");

    static final FakeMerchant MERCHANT = FakeMerchant.start();

    /**
     * ParcelStatusService checks for delivery proof before DELIVERED (courier module). These
     * tests never reach a valid DELIVERED change, so an empty mock is enough.
     */
    @MockitoBean
    private DeliveryProofRepository deliveryProofRepository;

    @Autowired
    private ParcelStatusService parcelStatusService;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private WebhookDeliveryRepository deliveryRepository;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void resetMerchant() {
        MERCHANT.reset();
    }

    @AfterAll
    static void stopMerchant() {
        MERCHANT.stop();
    }

    @Test
    void statusUpdateReachesMerchantAsSignedWebhook() {
        Parcel parcel = merchantParcel();

        parcelStatusService.updateStatus(parcel.getId(), ParcelStatus.PICKED_UP, "Collected from shop", null);

        WebhookDelivery delivery = awaitDelivery(parcel, WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getLastResponseCode()).isEqualTo(200);

        List<OutboxEvent> outbox = outboxRepository.findByAggregateIdOrderByCreatedAt(parcel.getId());
        assertThat(outbox).hasSize(1);
        assertThat(outbox.getFirst().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outbox.getFirst().getRoutingKey()).isEqualTo("parcel.status.picked_up");
        assertThat(delivery.getEventId()).isEqualTo(outbox.getFirst().getId());

        assertThat(MERCHANT.requests()).hasSize(1);
        FakeMerchant.Request request = MERCHANT.requests().getFirst();
        assertThat(new WebhookSigner().verify(SECRET, request.header(WebhookSigner.SIGNATURE_HEADER), request.body(),
                Instant.now(), Duration.ofMinutes(5))).isTrue();

        JsonNode body = jsonMapper.readTree(request.body());
        assertThat(body.get("id").asString()).isEqualTo(outbox.getFirst().getId().toString());
        assertThat(body.get("data").get("trackingNumber").asString()).isEqualTo(parcel.getTrackingNumber());
        assertThat(body.get("data").get("status").asString()).isEqualTo("PICKED_UP");
        assertThat(body.get("data").get("previousStatus").asString()).isEqualTo("CREATED");
        assertThat(body.get("data").get("note").asString()).isEqualTo("Collected from shop");
    }

    @Test
    void failingMerchantIsRetriedThroughRetryQueuesUntilItAccepts() {
        Parcel parcel = merchantParcel();
        MERCHANT.failNext(2);

        parcelStatusService.updateStatus(parcel.getId(), ParcelStatus.PICKED_UP, null, null);

        WebhookDelivery delivery = awaitDelivery(parcel, WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
        assertThat(MERCHANT.requests()).extracting(r -> r.header("X-SwiftDrop-Attempt"))
                .containsExactly("1", "2", "3");
        assertThat(MERCHANT.requests()).extracting(r -> r.header("X-SwiftDrop-Event-Id"))
                .containsOnly(delivery.getEventId().toString());
        assertThat(MERCHANT.requests()).extracting(FakeMerchant.Request::body).containsOnly(delivery.getPayload());
    }

    @Test
    void deliveryThatNeverSucceedsEndsInDeadLetterQueue() {
        Parcel parcel = merchantParcel();
        MERCHANT.failNext(Integer.MAX_VALUE);

        parcelStatusService.updateStatus(parcel.getId(), ParcelStatus.PICKED_UP, null, null);

        WebhookDelivery delivery = awaitDelivery(parcel, WebhookDeliveryStatus.DEAD);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
        assertThat(delivery.getLastResponseCode()).isEqualTo(500);

        Message deadLetter = rabbitTemplate.receive(MessagingTopology.WEBHOOK_DEAD_LETTER_QUEUE, 5000);
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).contains(delivery.getId().toString());
    }

    @Test
    void rejectedStatusChangeLeavesNoEventBehind() {
        Parcel parcel = merchantParcel();

        assertThatThrownBy(() -> parcelStatusService.updateStatus(parcel.getId(), ParcelStatus.DELIVERED, null, null))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(outboxRepository.findByAggregateIdOrderByCreatedAt(parcel.getId())).isEmpty();
    }

    @Test
    void customerParcelIsPublishedButSendsNoWebhook() {
        Parcel parcel = parcelRepository.save(newParcel());

        parcelStatusService.updateStatus(parcel.getId(), ParcelStatus.PICKED_UP, null, null);

        await().atMost(WAIT).untilAsserted(() -> assertThat(outboxRepository.findByAggregateIdOrderByCreatedAt(
                parcel.getId())).extracting(OutboxEvent::getStatus).containsExactly(OutboxStatus.PUBLISHED));
        assertThat(deliveryRepository.findAll()).noneMatch(d -> parcel.getId().equals(d.getParcelId()));
        assertThat(MERCHANT.requests()).isEmpty();
    }

    private Parcel merchantParcel() {
        Parcel parcel = parcelRepository.save(newParcel());
        TestApplication.MERCHANT_PARCELS.add(parcel.getId());
        return parcel;
    }

    private WebhookDelivery awaitDelivery(Parcel parcel, WebhookDeliveryStatus expected) {
        return await().atMost(WAIT).until(
                () -> deliveryRepository.findAll().stream()
                        .filter(d -> parcel.getId().equals(d.getParcelId()))
                        .filter(d -> d.getStatus() == expected)
                        .findFirst(),
                Optional::isPresent).orElseThrow();
    }

    private static Parcel newParcel() {
        return Parcel.builder()
                .trackingNumber("SDP" + (1_000_000 + (int) (Math.random() * 8_999_999)))
                .senderName("Kandy Crafts")
                .recipientName("Nimal Silva")
                .pickupAddress("No. 415, Cotta Road")
                .pickupCity("Colombo")
                .dropoffAddress("No. 12, Galle Road")
                .dropoffCity("Dehiwala")
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("2.50"))
                .fee(new BigDecimal("590.00"))
                .build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Parcel.class, ParcelStatusHistory.class, OutboxEvent.class,
            WebhookDelivery.class})
    @EnableJpaRepositories(basePackageClasses = {ParcelRepository.class, ParcelStatusHistoryRepository.class,
            OutboxEventRepository.class, WebhookDeliveryRepository.class})
    @Import({ClockConfig.class, ParcelStatusService.class, RabbitMqConfig.class, OutboxService.class,
            OutboxEventRecorder.class, OutboxPublisher.class, WebhookHttpClient.class, WebhookSigner.class,
            RetryPolicy.class, WebhookRetryScheduler.class, WebhookDispatchService.class, WebhookConsumer.class})
    static class TestApplication {

        /** Parcels that "belong" to the test merchant; any other parcel is a customer booking. */
        static final Set<UUID> MERCHANT_PARCELS = ConcurrentHashMap.newKeySet();

        @Bean
        MerchantWebhookDirectory merchantWebhookDirectory() {
            return new MerchantWebhookDirectory() {
                @Override
                public Optional<UUID> findMerchantIdForParcel(UUID parcelId) {
                    return MERCHANT_PARCELS.contains(parcelId) ? Optional.of(MERCHANT_ID) : Optional.empty();
                }

                @Override
                public Optional<WebhookEndpoint> findEndpoint(UUID merchantId) {
                    return Optional.of(new WebhookEndpoint(merchantId, MERCHANT.url(), SECRET));
                }

                @Override
                public Optional<UUID> findMerchantIdForUser(String email) {
                    return Optional.empty();
                }
            };
        }
    }

    /**
     * A merchant's webhook receiver: records every request and can be told to fail.
     */
    static final class FakeMerchant {

        record Request(String body, Map<String, String> headers) {
            String header(String name) {
                return headers.get(name.toLowerCase());
            }
        }

        private final HttpServer server;
        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private final AtomicInteger failuresLeft = new AtomicInteger();

        private FakeMerchant(HttpServer server) {
            this.server = server;
        }

        static FakeMerchant start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                FakeMerchant merchant = new FakeMerchant(server);
                server.createContext("/webhooks", exchange -> {
                    Map<String, String> headers = new ConcurrentHashMap<>();
                    exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.getFirst()));
                    merchant.requests.add(new Request(
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), headers));
                    int status = merchant.failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0 ? 500 : 200;
                    exchange.sendResponseHeaders(status, -1);
                    exchange.close();
                });
                server.start();
                return merchant;
            } catch (IOException ex) {
                throw new IllegalStateException("Could not start fake merchant", ex);
            }
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhooks";
        }

        List<Request> requests() {
            return requests;
        }

        void failNext(int times) {
            failuresLeft.set(times);
        }

        void reset() {
            requests.clear();
            failuresLeft.set(0);
        }

        void stop() {
            server.stop(0);
        }
    }
}
