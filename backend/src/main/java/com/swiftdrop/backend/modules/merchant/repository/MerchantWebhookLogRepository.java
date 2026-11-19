package com.swiftdrop.backend.modules.merchant.repository;

import com.swiftdrop.backend.modules.merchant.entity.MerchantWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MerchantWebhookLogRepository extends JpaRepository<MerchantWebhookLog, UUID> {

    List<MerchantWebhookLog> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<MerchantWebhookLog> findByTrackingNumberOrderByCreatedAtDesc(String trackingNumber);
}

