package com.swiftdrop.backend.modules.messaging.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.messaging.event.ParcelEvents;
import com.swiftdrop.backend.modules.messaging.event.ParcelStatusChangedPayload;
import com.swiftdrop.backend.modules.messaging.service.OutboxService;
import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent; //where it takes the status change 

import lombok.RequiredArgsConstructor;

/**
 * Turns every parcel status change into an outbox row (Observer pattern). A plain
 * {@code @EventListener} runs inside updateStatus()'s transaction, so the status, its
 * history entry and the outgoing event are saved together or not at all.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxService outboxService;

    @EventListener
    public void onParcelStatusChanged(ParcelStatusChangedEvent event) {
        outboxService.publishEvent(
                ParcelEvents.AGGREGATE_TYPE,
                event.parcelId(),
                ParcelEvents.STATUS_CHANGED,
                ParcelEvents.statusRoutingKey(event.newStatus()),
                ParcelStatusChangedPayload.from(event));
    }
}
