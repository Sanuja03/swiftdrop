package com.swiftdrop.backend.modules.webhook.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.swiftdrop.backend.modules.webhook.endpoint.MerchantWebhookDirectory;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.backend.modules.webhook.exception.WebhookException;
import com.swiftdrop.backend.modules.webhook.repository.WebhookDeliveryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads the webhook delivery log and handles manual retries. Merchant methods are always
 * scoped to the logged-in merchant, so one merchant can never see another's deliveries.
 */
@Service
@RequiredArgsConstructor
public class WebhookDeliveryService {

    private final WebhookDeliveryRepository repository;
    private final MerchantWebhookDirectory directory;
    private final WebhookDispatchService dispatchService;

    public Page<WebhookDelivery> listForMerchant(String userEmail, WebhookDeliveryStatus status, Pageable pageable) {
        UUID merchantId = merchantIdOf(userEmail);
        return status == null
                ? repository.findByMerchantId(merchantId, pageable)
                : repository.findByMerchantIdAndStatus(merchantId, status, pageable);
    }

    public WebhookDelivery getForMerchant(String userEmail, UUID deliveryId) {
        UUID merchantId = merchantIdOf(userEmail);
        return repository.findById(deliveryId)
                .filter(delivery -> delivery.getMerchantId().equals(merchantId))
                .orElseThrow(() -> WebhookException.deliveryNotFound(deliveryId));
    }

    public WebhookDelivery retryForMerchant(String userEmail, UUID deliveryId) {
        return retry(getForMerchant(userEmail, deliveryId));
    }

    public Page<WebhookDelivery> listAll(UUID merchantId, WebhookDeliveryStatus status, Pageable pageable) {
        if (merchantId != null && status != null) {
            return repository.findByMerchantIdAndStatus(merchantId, status, pageable);
        }
        if (merchantId != null) {
            return repository.findByMerchantId(merchantId, pageable);
        }
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        return repository.findAll(pageable);
    }

    public WebhookDelivery get(UUID deliveryId) {
        return repository.findById(deliveryId).orElseThrow(() -> WebhookException.deliveryNotFound(deliveryId));
    }

    public WebhookDelivery retryAsAdmin(UUID deliveryId) {
        return retry(get(deliveryId));
    }

    private WebhookDelivery retry(WebhookDelivery delivery) {
        if (!delivery.canRetryManually()) {
            throw WebhookException.notRetryable();
        }
        return dispatchService.retryNow(delivery);
    }

    private UUID merchantIdOf(String userEmail) {
        return directory.findMerchantIdForUser(userEmail).orElseThrow(WebhookException::merchantAccountMissing);
    }
}
