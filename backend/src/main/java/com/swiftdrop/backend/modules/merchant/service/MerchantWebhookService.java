package com.swiftdrop.backend.modules.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.modules.merchant.dto.MerchantWebhookLogDto;
import com.swiftdrop.backend.modules.merchant.dto.WebhookTestResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantWebhookLog;
import com.swiftdrop.backend.modules.merchant.entity.WebhookDeliveryStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.merchant.repository.MerchantWebhookLogRepository;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.webhook.client.WebhookHttpClient;
import com.swiftdrop.backend.modules.webhook.client.WebhookHttpResult;
import com.swiftdrop.backend.modules.webhook.endpoint.JdbcMerchantWebhookDirectory;
import com.swiftdrop.backend.modules.webhook.exception.WebhookException;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery;
import com.swiftdrop.backend.modules.webhook.repository.WebhookDeliveryRepository;
import com.swiftdrop.backend.modules.webhook.signing.WebhookSigner;
import com.swiftdrop.backend.modules.webhook.validation.WebhookUrlValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MerchantWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MerchantWebhookService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantWebhookLogRepository webhookLogRepository;
    private final JdbcMerchantWebhookDirectory directory;
    private final WebhookUrlValidator validator;
    private final WebhookSigner signer;
    private final WebhookHttpClient sharedHttpClient;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    // Off by default: parcel status webhooks are delivered by the shared messaging & webhook engine
    // (outbox -> RabbitMQ -> WebhookDispatchService). This service keeps the webhook test and log feature.
    @Value("${swiftdrop.webhooks.direct-delivery.enabled:false}")
    private boolean directDeliveryEnabled = false;

    public MerchantWebhookService(MerchantRepository merchantRepository,
                                  MerchantWebhookLogRepository webhookLogRepository,
                                  JdbcMerchantWebhookDirectory directory, WebhookUrlValidator validator,
                                  WebhookSigner signer, WebhookHttpClient sharedHttpClient,
                                  WebhookDeliveryRepository deliveries) {
        this.merchantRepository = merchantRepository;
        this.webhookLogRepository = webhookLogRepository;
        this.directory = directory;
        this.validator = validator;
        this.signer = signer;
        this.sharedHttpClient = sharedHttpClient;
        this.deliveries = deliveries;
    }

    @Async
    public void notifyStatusChangeAsync(Parcel parcel, String eventType, String note) {
        if (!directDeliveryEnabled) {
            log.debug("Direct webhook notification skipped for parcel {} (messaging engine active)", parcel.getTrackingNumber());
            return;
        }

        if (parcel.getMerchantId() == null) {
            return;
        }

        merchantRepository.findById(parcel.getMerchantId()).ifPresent(merchant -> {
            if (merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isBlank()) {
                return;
            }
            sendWebhook(merchant, parcel, eventType, note, 1);
        });
    }

    public void sendWebhook(Merchant merchant, Parcel parcel, String eventType, String note, int attemptCount) {
        // Legacy direct mode remains off. If explicitly enabled, it must obey the same eligibility/URL policy.
        var endpoint = directory.findEndpoint(merchant.getId());
        if (endpoint.isEmpty()) return;
        try {
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("event", eventType);
            payloadMap.put("merchantId", merchant.getId());
            payloadMap.put("trackingNumber", parcel.getTrackingNumber());
            payloadMap.put("status", parcel.getStatus().name());
            payloadMap.put("statusLabel", parcel.getStatus().getLabel());
            payloadMap.put("serviceType", parcel.getServiceType() != null ? parcel.getServiceType().name() : null);
            payloadMap.put("fee", parcel.getFee());
            payloadMap.put("currency", "LKR");
            payloadMap.put("note", note != null ? note : "Parcel status updated to " + parcel.getStatus().getLabel());
            payloadMap.put("timestamp", Instant.now().toString());

            // Privacy: no recipient phone or address in webhook body (as per security guidelines)
            Map<String, Object> recipient = new LinkedHashMap<>();
            recipient.put("name", parcel.getRecipientName());
            recipient.put("city", parcel.getDropoffCity());
            payloadMap.put("recipient", recipient);

            long timestamp = Instant.now().getEpochSecond();
            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            String signature = signer.sign(endpoint.get().secret(), timestamp, jsonPayload);
            String deliveryId = UUID.randomUUID().toString();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.get().url()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SwiftDrop-Webhook/1.0")
                    .header("X-SwiftDrop-Signature", signature)
                    .header("X-SwiftDrop-Event", eventType)
                    .header("X-SwiftDrop-Delivery", deliveryId)
                    .header("X-SwiftDrop-Attempt", String.valueOf(attemptCount))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8));

            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            boolean isSuccess = response.statusCode() >= 200 && response.statusCode() < 300;
            WebhookDeliveryStatus deliveryStatus = isSuccess ? WebhookDeliveryStatus.SUCCESS : WebhookDeliveryStatus.RETRYING;

            MerchantWebhookLog webhookLog = MerchantWebhookLog.builder()
                    .merchantId(merchant.getId())
                    .trackingNumber(parcel.getTrackingNumber())
                    .eventType(eventType)
                    .payload(jsonPayload)
                    .endpointUrl(merchant.getWebhookUrl())
                    .responseCode(response.statusCode())
                    .deliveryStatus(deliveryStatus)
                    .errorMessage(isSuccess ? null : "HTTP " + response.statusCode() + " received after " + latency + "ms")
                    .attemptCount(attemptCount)
                    .build();

            webhookLogRepository.save(webhookLog);
            log.info("Webhook dispatched to {} [status={}, attempt={}]", merchant.getWebhookUrl(), response.statusCode(), attemptCount);

        } catch (Exception ex) {
            log.warn("Webhook dispatch failed to {}: {}", merchant.getWebhookUrl(), ex.getMessage());
            MerchantWebhookLog failedLog = MerchantWebhookLog.builder()
                    .merchantId(merchant.getId())
                    .trackingNumber(parcel.getTrackingNumber())
                    .eventType(eventType)
                    .payload("{\"error\": \"" + ex.getMessage() + "\"}")
                    .endpointUrl(merchant.getWebhookUrl())
                    .responseCode(0)
                    .deliveryStatus(WebhookDeliveryStatus.FAILED)
                    .errorMessage("Connection error: " + ex.getMessage())
                    .attemptCount(attemptCount)
                    .build();
            webhookLogRepository.save(failedLog);
        }
    }

    /** A one-shot test using the production URL policy, transport and signature contract. */
    public WebhookTestResponse testWebhook(UUID merchantId, String overrideUrl) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(WebhookException::merchantAccountMissing);
        if (merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isBlank()
                || merchant.getWebhookSecret() == null || merchant.getWebhookSecret().isBlank()) {
            throw WebhookException.configurationRequired();
        }
        String targetUrl = validator.validate(overrideUrl != null && !overrideUrl.isBlank()
                ? overrideUrl : merchant.getWebhookUrl());
        var endpoint = directory.findEndpoint(merchantId).orElseThrow(WebhookException::merchantAccountMissing);
        Instant now = Instant.now();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("event", "webhook.test_ping");
        payload.put("merchantId", merchantId);
        payload.put("businessName", merchant.getBusinessName());
        payload.put("message", "SwiftDrop webhook test ping");
        payload.put("timestamp", now.toString());
        final String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize webhook test");
        }
        WebhookDelivery delivery = deliveries.save(WebhookDelivery.create(eventId, merchantId, null,
                "TEST-PING", "webhook.test_ping", targetUrl, body, 1));
        long start = System.nanoTime();
        WebhookHttpResult result = sharedHttpClient.post(targetUrl, body, Map.of(
                "Content-Type", "application/json", "User-Agent", "SwiftDrop-Webhooks/1.0",
                "X-SwiftDrop-Event", "webhook.test_ping", "X-SwiftDrop-Event-Id", eventId.toString(),
                "X-SwiftDrop-Delivery", delivery.getId().toString(), "X-SwiftDrop-Attempt", "1",
                WebhookSigner.SIGNATURE_HEADER, signer.sign(endpoint.secret(), now.getEpochSecond(), body)));
        if (result.isSuccess()) {
            delivery.recordSuccess(targetUrl, result.statusCode(), Instant.now());
        } else {
            delivery.recordFailure(targetUrl, result.statusCode(), result.error(), Instant.now());
            delivery.markDead();
        }
        deliveries.save(delivery);
        return WebhookTestResponse.builder().success(result.isSuccess())
                .statusCode(result.statusCode() == null ? 0 : result.statusCode())
                .message(result.isSuccess() ? "Ping succeeded (HTTP " + result.statusCode() + ")" : result.error())
                .latencyMs((System.nanoTime() - start) / 1_000_000)
                .endpointUrl(targetUrl).simulatedPayload(body).build();
    }

    /** Compatibility list shape, backed by the actual delivery engine's records. */
    public List<MerchantWebhookLogDto> getRecentLogs(UUID merchantId) {
        return deliveries.findByMerchantId(merchantId, PageRequest.of(0, 50, Sort.by("createdAt").descending()))
                .stream().map(delivery -> MerchantWebhookLogDto.builder()
                        .id(delivery.getId()).merchantId(delivery.getMerchantId())
                        .trackingNumber(delivery.getTrackingNumber()).eventType(delivery.getEventType())
                        .payload(delivery.getPayload()).endpointUrl(delivery.getEndpointUrl())
                        .responseCode(delivery.getLastResponseCode())
                        .deliveryStatus(switch (delivery.getStatus()) {
                            case DELIVERED -> WebhookDeliveryStatus.SUCCESS;
                            case DEAD -> WebhookDeliveryStatus.FAILED;
                            case PENDING, RETRYING -> WebhookDeliveryStatus.RETRYING;
                        })
                        .errorMessage(delivery.getLastError()).attemptCount(delivery.getAttemptCount())
                        .createdAt(delivery.getCreatedAt()).build()).toList();
    }

    public String signPayload(String payload, String secret) {
        return signer.sign(secret, Instant.now().getEpochSecond(), payload);
    }

    public String signPayload(String payload, String secret, long timestamp) {
        return signer.sign(secret, timestamp, payload);
    }
}
