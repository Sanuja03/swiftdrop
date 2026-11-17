package com.swiftdrop.backend.modules.webhook.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDeliveryStatus;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    boolean existsByEventIdAndMerchantId(UUID eventId, UUID merchantId);

    Page<WebhookDelivery> findByMerchantId(UUID merchantId, Pageable pageable);

    Page<WebhookDelivery> findByMerchantIdAndStatus(UUID merchantId, WebhookDeliveryStatus status, Pageable pageable);

    Page<WebhookDelivery> findByStatus(WebhookDeliveryStatus status, Pageable pageable);

    List<WebhookDelivery> findByEventId(UUID eventId);
}
