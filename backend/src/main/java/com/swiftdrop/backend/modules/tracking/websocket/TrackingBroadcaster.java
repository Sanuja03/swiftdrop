package com.swiftdrop.backend.modules.tracking.websocket;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pushes every status change to the customers watching that parcel.
 * Runs only after the change is committed, so customers never see a status
 * that was rolled back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ParcelStatusChangedEvent event) {
        String destination = TrackingTopics.forTrackingNumber(event.trackingNumber());
        try {
            messagingTemplate.convertAndSend(destination, TrackingUpdateMessage.from(event));
        } catch (MessagingException ex) {
            // The status is already saved; a missed push only delays the page until it refreshes.
            log.warn("Could not push tracking update to {}", destination, ex);
        }
    }
}
