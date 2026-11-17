package com.swiftdrop.backend.modules.messaging.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.backend.modules.messaging.event.ParcelStatusChangedPayload;
import com.swiftdrop.backend.modules.messaging.service.OutboxService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent;

@ExtendWith(MockitoExtension.class)
class OutboxEventRecorderTest {

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private OutboxEventRecorder recorder;

    @Test
    void statusChangeIsWrittenToOutboxWithStatusRoutingKey() {
        UUID parcelId = UUID.randomUUID();
        Instant changedAt = Instant.parse("2026-11-13T09:30:00Z");
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(parcelId, "SDP7845621",
                ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, "Signed by Nimal", UUID.randomUUID(), changedAt);

        recorder.onParcelStatusChanged(event);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publishEvent(eq("parcel"), eq(parcelId), eq("parcel.status_changed"),
                eq("parcel.status.delivered"), payload.capture());
        assertThat(payload.getValue()).isEqualTo(new ParcelStatusChangedPayload(parcelId, "SDP7845621",
                ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, "Signed by Nimal", changedAt));
    }

    @Test
    void newParcelHasNoPreviousStatus() {
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(parcelId, "SDP7845621",
                null, ParcelStatus.CREATED, null, null, Instant.now());

        recorder.onParcelStatusChanged(event);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publishEvent(eq("parcel"), eq(parcelId), eq("parcel.status_changed"),
                eq("parcel.status.created"), payload.capture());
        assertThat(((ParcelStatusChangedPayload) payload.getValue()).previousStatus()).isNull();
    }
}
