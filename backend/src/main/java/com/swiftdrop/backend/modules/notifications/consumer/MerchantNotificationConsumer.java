package com.swiftdrop.backend.modules.notifications.consumer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.event.MerchantRegisteredPayload;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.notifications.config.NotificationRabbitMqConfig;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** In-app alerts only, with one persisted notification per event and operations user. */
@Component
@RequiredArgsConstructor
public class MerchantNotificationConsumer {
    private final UserRepository users;
    private final MerchantRepository merchants;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;

    @RabbitListener(queues = NotificationRabbitMqConfig.MERCHANT_NOTIFICATION_QUEUE)
    public void onMerchantEvent(DomainEventMessage event) {
        if (!MerchantRegisteredPayload.EVENT_TYPE.equals(event.eventType())) return;
        var payload = jsonMapper.treeToValue(event.payload(), MerchantRegisteredPayload.class);
        var merchant = merchants.findById(payload.merchantId()).orElse(null);
        if (merchant == null || merchant.getRegistrationStatus() != MerchantRegistrationStatus.PENDING) return;

        for (Role role : new Role[]{Role.ROLE_ADMIN}) {
            for (var user : users.findByRole(role)) {
                if (user.getStatus() != AccountStatus.ACTIVE) continue;
                UUID recipientEventId = UUID.nameUUIDFromBytes(
                        (event.eventId() + ":" + user.getId()).getBytes(StandardCharsets.UTF_8));
                if (notifications.existsByEventId(recipientEventId)) continue;
                try {
                    notificationService.createEventNotification(recipientEventId, user.getId(), null,
                            "Merchant Approval Pending",
                            merchant.getBusinessName() + " has submitted a merchant application. Review it in Merchant Approvals.",
                            "MERCHANT_APPROVAL");
                } catch (DataIntegrityViolationException ex) {
                    if (!notifications.existsByEventId(recipientEventId)) throw ex;
                }
            }
        }
    }
}
