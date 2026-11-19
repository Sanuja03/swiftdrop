package com.swiftdrop.backend.modules.notifications.consumer;

import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.messaging.event.ParcelEvents;
import com.swiftdrop.backend.modules.messaging.event.ParcelStatusChangedPayload;
import com.swiftdrop.backend.modules.notifications.config.NotificationRabbitMqConfig;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;
import com.swiftdrop.backend.modules.notifications.service.NotificationDeliveryService;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationConsumer.class);

    private final ParcelRepository parcelRepository;
    private final NotificationService notificationService;
    private final NotificationDeliveryService deliveryService;
    private final NotificationRepository notificationRepository;
    private final JsonMapper jsonMapper;

    public NotificationConsumer(
            ParcelRepository parcelRepository,
            NotificationService notificationService,
            NotificationDeliveryService deliveryService,
            NotificationRepository notificationRepository,
            JsonMapper jsonMapper
    ) {
        this.parcelRepository = parcelRepository;
        this.notificationService = notificationService;
        this.deliveryService = deliveryService;
        this.notificationRepository = notificationRepository;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = NotificationRabbitMqConfig.NOTIFICATION_QUEUE)
    public void onParcelEvent(DomainEventMessage event) {

        if (!ParcelEvents.STATUS_CHANGED.equals(event.eventType())) {
            return;
        }

        if (notificationRepository.existsByEventId(event.eventId())) {
            log.info("Duplicate notification event {} ignored", event.eventId());
            return;
        }
        ParcelStatusChangedPayload payload =
                jsonMapper.treeToValue(
                        event.payload(),
                        ParcelStatusChangedPayload.class
                );

        Optional<Parcel> optionalParcel =
                parcelRepository.findById(payload.parcelId());

        if (optionalParcel.isEmpty()) {
            log.warn(
                    "Notification skipped: parcel {} not found",
                    payload.parcelId()
            );
            return;
        }

        Parcel parcel = optionalParcel.get();
        UUID customerId = parcel.getCustomerId();

        if (customerId == null) {
            log.warn(
                    "Notification skipped: parcel {} has no customer",
                    parcel.getId()
            );
            return;
        }

        String status = payload.status().name();

        String title = "Parcel Status Updated";

        String message = "Your parcel "
                + payload.trackingNumber()
                + " is now "
                + status.replace("_", " ").toLowerCase()
                + ".";

        try {
            notificationService.createEventNotification(
                    event.eventId(),
                    customerId,
                    parcel.getId(),
                    title,
                    message,
                    "PARCEL_STATUS"
            );
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            if (notificationRepository.existsByEventId(event.eventId())) {
                log.info("Duplicate notification event {} ignored", event.eventId());
                return;
            }
            throw ex;
        }
        deliveryService.deliver(customerId, title, message);

        log.info(
                "Notification created for customer {} and parcel {}",
                customerId,
                parcel.getId()
        );
    }
}
