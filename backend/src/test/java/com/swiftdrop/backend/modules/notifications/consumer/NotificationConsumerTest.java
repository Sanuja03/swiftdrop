package com.swiftdrop.backend.modules.notifications.consumer;

import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.messaging.event.ParcelEvents;
import com.swiftdrop.backend.modules.messaging.event.ParcelStatusChangedPayload;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;
import com.swiftdrop.backend.modules.notifications.service.NotificationDeliveryService;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationConsumerTest {
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService service = mock(NotificationService.class);
    private final NotificationDeliveryService delivery = mock(NotificationDeliveryService.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID parcelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationConsumer(parcels, service, delivery, notifications, mapper);
    }

    private DomainEventMessage event(String type) {
        var payload = new ParcelStatusChangedPayload(parcelId, "TEST123", ParcelStatus.CREATED,
                ParcelStatus.PICKED_UP, null, Instant.now());
        return new DomainEventMessage(eventId, type, "PARCEL", parcelId, Instant.now(),
                mapper.valueToTree(payload));
    }

    private void existingParcel() {
        Parcel parcel = mock(Parcel.class);
        when(parcel.getId()).thenReturn(parcelId);
        when(parcel.getCustomerId()).thenReturn(customerId);
        when(parcels.findById(parcelId)).thenReturn(Optional.of(parcel));
    }

    @Test
    void createsAndDeliversStatusNotification() {
        existingParcel();
        consumer.onParcelEvent(event(ParcelEvents.STATUS_CHANGED));
        verify(service).createEventNotification(eventId, customerId, parcelId,
                "Parcel Status Updated", "Your parcel TEST123 is now picked up.", "PARCEL_STATUS");
        verify(delivery).deliver(customerId, "Parcel Status Updated", "Your parcel TEST123 is now picked up.");
    }

    @Test
    void ignoresUnrelatedEvents() {
        consumer.onParcelEvent(event("unrelated"));
        verifyNoInteractions(parcels, notifications, service, delivery);
    }

    @Test
    void skipsPreviouslyProcessedEvents() {
        when(notifications.existsByEventId(eventId)).thenReturn(true);
        consumer.onParcelEvent(event(ParcelEvents.STATUS_CHANGED));
        verifyNoInteractions(parcels, service, delivery);
    }

    @Test
    void skipsMissingParcels() {
        when(parcels.findById(parcelId)).thenReturn(Optional.empty());
        consumer.onParcelEvent(event(ParcelEvents.STATUS_CHANGED));
        verifyNoInteractions(service, delivery);
    }

    @Test
    void skipsParcelsWithoutCustomers() {
        when(parcels.findById(parcelId)).thenReturn(Optional.of(mock(Parcel.class)));
        consumer.onParcelEvent(event(ParcelEvents.STATUS_CHANGED));
        verifyNoInteractions(service, delivery);
    }

    @Test
    void concurrentDuplicateDoesNotDeliverAgain() {
        existingParcel();
        when(notifications.existsByEventId(eventId)).thenReturn(false, true);
        when(service.createEventNotification(any(), any(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate event"));
        consumer.onParcelEvent(event(ParcelEvents.STATUS_CHANGED));
        verifyNoInteractions(delivery);
    }

    @Test
    void unrelatedPersistenceFailureIsRetriedByListener() {
        existingParcel();
        when(service.createEventNotification(any(), any(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("storage failure"));
        assertThrows(DataIntegrityViolationException.class,
                () -> consumer.onParcelEvent(event(ParcelEvents.STATUS_CHANGED)));
        verifyNoInteractions(delivery);
    }
}
