package com.swiftdrop.backend.modules.notifications.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import com.swiftdrop.backend.modules.notifications.dto.MerchantDecisionContent;
import com.swiftdrop.backend.modules.notifications.config.NotificationRabbitMqConfig;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;
import com.swiftdrop.backend.modules.messaging.config.MessagingTopology;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class MerchantDecisionNotificationConsumer {
    private final NotificationRepository notifications;
    private final NotificationService service;
    private final JsonMapper mapper;

    @RabbitListener(queues = NotificationRabbitMqConfig.DECISION_NOTIFICATION_QUEUE,
            containerFactory = MessagingTopology.WEBHOOK_LISTENER_FACTORY)
    public void onDecision(DomainEventMessage event) {
        if (notifications.existsByEventId(event.eventId())) return;
        var value = MerchantDecisionContent.from(event, mapper);
        if (value == null) return;
        try {
            service.createEventNotification(event.eventId(), value.userId(), null, value.title(), value.message(), value.type());
        } catch (DataIntegrityViolationException ex) {
            if (!notifications.existsByEventId(event.eventId())) throw ex;
        }
    }
}
