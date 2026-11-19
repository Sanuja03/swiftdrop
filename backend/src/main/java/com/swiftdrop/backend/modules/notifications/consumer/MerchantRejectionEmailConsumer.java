package com.swiftdrop.backend.modules.notifications.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.swiftdrop.backend.modules.messaging.config.MessagingTopology;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.notifications.config.NotificationRabbitMqConfig;
import com.swiftdrop.backend.modules.notifications.dto.MerchantDecisionContent;
import com.swiftdrop.backend.modules.notifications.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** Delivers approval and rejection email; in-app delivery runs independently. */
@Component
@RequiredArgsConstructor
public class MerchantRejectionEmailConsumer {
    private final NotificationDeliveryService delivery;
    private final JsonMapper mapper;

    @RabbitListener(queues = {NotificationRabbitMqConfig.REJECTION_EMAIL_QUEUE, NotificationRabbitMqConfig.APPROVAL_EMAIL_QUEUE},
            containerFactory = MessagingTopology.WEBHOOK_LISTENER_FACTORY)
    public void onDecision(DomainEventMessage event) {
        var value = MerchantDecisionContent.from(event, mapper);
        if (value != null) delivery.deliverRequiredEmail(value.userId(), value.title(), value.message());
    }
}
