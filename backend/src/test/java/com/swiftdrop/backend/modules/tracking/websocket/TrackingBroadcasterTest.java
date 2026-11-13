package com.swiftdrop.backend.modules.tracking.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent;

@ExtendWith(MockitoExtension.class)
class TrackingBroadcasterTest {

    private static final Instant CHANGED_AT = Instant.parse("2026-11-05T10:15:00Z");

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private TrackingBroadcaster broadcaster;

    private final ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
            UUID.randomUUID(), "SDP7845621", ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY,
            "Recipient's home address note", UUID.randomUUID(), CHANGED_AT);

    @Test
    void pushesStatusToTheParcelsTrackingTopic() {
        broadcaster.onStatusChanged(event);

        verify(messagingTemplate).convertAndSend("/topic/tracking/SDP7845621",
                new TrackingUpdateMessage("SDP7845621", ParcelStatus.OUT_FOR_DELIVERY, "Out for Delivery", CHANGED_AT));
    }

    @Test
    void messageNeverContainsTheNote() {
        Assertions.assertThat(TrackingUpdateMessage.from(event).toString())
                .doesNotContain("Recipient's home address note");
    }

    @Test
    void pushFailureDoesNotPropagate() {
        doThrow(new MessageDeliveryException("broker down"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        Assertions.assertThatCode(() -> broadcaster.onStatusChanged(event)).doesNotThrowAnyException();
    }
}
