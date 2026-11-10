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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MerchantWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MerchantWebhookService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantWebhookLogRepository webhookLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public MerchantWebhookService(MerchantRepository merchantRepository,
                                  MerchantWebhookLogRepository webhookLogRepository) {
        this.merchantRepository = merchantRepository;
        this.webhookLogRepository = webhookLogRepository;
    }

    @Async
    public void notifyStatusChangeAsync(Parcel parcel, String eventType, String note) {
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

            Map<String, Object> recipient = new LinkedHashMap<>();
            recipient.put("name", parcel.getRecipientName());
            recipient.put("phone", parcel.getRecipientPhone());
            recipient.put("address", parcel.getDropoffAddress());
            recipient.put("city", parcel.getDropoffCity());
            payloadMap.put("recipient", recipient);

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            String signature = signPayload(jsonPayload, merchant.getWebhookSecret());

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(merchant.getWebhookUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SwiftDrop-Webhook/1.0")
                    .header("X-SwiftDrop-Signature", signature)
                    .header("X-SwiftDrop-Event", eventType)
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

    public WebhookTestResponse testWebhook(UUID merchantId, String overrideUrl) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));

        String targetUrl = (overrideUrl != null && !overrideUrl.isBlank()) ? overrideUrl : merchant.getWebhookUrl();
        if (targetUrl == null || targetUrl.isBlank()) {
            return WebhookTestResponse.builder()
                    .success(false)
                    .statusCode(400)
                    .message("No webhook URL configured or provided")
                    .endpointUrl(targetUrl)
                    .build();
        }

        try {
            Map<String, Object> testPayload = new LinkedHashMap<>();
            testPayload.put("event", "webhook.test_ping");
            testPayload.put("merchantId", merchant.getId());
            testPayload.put("businessName", merchant.getBusinessName());
            testPayload.put("message", "SwiftDrop webhook test ping successful");
            testPayload.put("timestamp", Instant.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(testPayload);
            String signature = signPayload(jsonPayload, merchant.getWebhookSecret());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(6))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SwiftDrop-Webhook-Test/1.0")
                    .header("X-SwiftDrop-Signature", signature)
                    .header("X-SwiftDrop-Event", "webhook.test_ping")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

            MerchantWebhookLog webhookLog = MerchantWebhookLog.builder()
                    .merchantId(merchant.getId())
                    .trackingNumber("TEST-PING")
                    .eventType("webhook.test_ping")
                    .payload(jsonPayload)
                    .endpointUrl(targetUrl)
                    .responseCode(response.statusCode())
                    .deliveryStatus(success ? WebhookDeliveryStatus.SUCCESS : WebhookDeliveryStatus.FAILED)
                    .errorMessage(success ? null : "Target endpoint responded with HTTP " + response.statusCode())
                    .attemptCount(1)
                    .build();
            webhookLogRepository.save(webhookLog);

            return WebhookTestResponse.builder()
                    .success(success)
                    .statusCode(response.statusCode())
                    .message(success ? "Ping succeeded (HTTP " + response.statusCode() + ")" : "Target returned error " + response.statusCode())
                    .latencyMs(latency)
                    .endpointUrl(targetUrl)
                    .simulatedPayload(jsonPayload)
                    .build();

        } catch (Exception ex) {
            return WebhookTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .message("Connection failed: " + ex.getMessage())
                    .latencyMs(0)
                    .endpointUrl(targetUrl)
                    .build();
        }
    }

    public List<MerchantWebhookLogDto> getRecentLogs(UUID merchantId) {
        return webhookLogRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                .stream()
                .limit(50)
                .map(l -> MerchantWebhookLogDto.builder()
                        .id(l.getId())
                        .merchantId(l.getMerchantId())
                        .trackingNumber(l.getTrackingNumber())
                        .eventType(l.getEventType())
                        .payload(l.getPayload())
                        .endpointUrl(l.getEndpointUrl())
                        .responseCode(l.getResponseCode())
                        .deliveryStatus(l.getDeliveryStatus())
                        .errorMessage(l.getErrorMessage())
                        .attemptCount(l.getAttemptCount())
                        .createdAt(l.getCreatedAt())
                        .build())
                .toList();
    }

    public String signPayload(String payload, String secret) {
        if (secret == null || secret.isBlank()) {
            secret = "default_swiftdrop_webhook_secret_key";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "sha256=unsigned";
        }
    }
}
