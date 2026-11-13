package com.swiftdrop.backend.modules.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.entity.ParcelStatusHistory;
import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent;
import com.swiftdrop.backend.modules.tracking.exception.InvalidStatusTransitionException;
import com.swiftdrop.backend.modules.tracking.repository.ParcelStatusHistoryRepository;
import com.swiftdrop.backend.support.TestParcels;

@ExtendWith(MockitoExtension.class)
class ParcelStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T09:30:00Z");
    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID COURIER_ID = UUID.randomUUID();

    @Mock
    private ParcelRepository parcelRepository;
    @Mock
    private ParcelStatusHistoryRepository historyRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ParcelStatusService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        service = new ParcelStatusService(parcelRepository, historyRepository, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        parcel = TestParcels.withId(PARCEL_ID);
    }

    @Test
    void validTransitionUpdatesParcelSavesHistoryAndAnnouncesChange() {
        when(parcelRepository.findById(PARCEL_ID)).thenReturn(Optional.of(parcel));

        service.updateStatus(PARCEL_ID, ParcelStatus.PICKED_UP, "  Collected from sender  ", COURIER_ID);

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);

        ArgumentCaptor<ParcelStatusHistory> history = ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getParcel()).isSameAs(parcel);
        assertThat(history.getValue().getFromStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(history.getValue().getToStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(history.getValue().getNote()).isEqualTo("Collected from sender");
        assertThat(history.getValue().getChangedBy()).isEqualTo(COURIER_ID);
        assertThat(history.getValue().getChangedAt()).isEqualTo(NOW);

        verify(eventPublisher).publishEvent(new ParcelStatusChangedEvent(
                PARCEL_ID, "SDP7845621", ParcelStatus.CREATED, ParcelStatus.PICKED_UP,
                "Collected from sender", COURIER_ID, NOW));
    }

    @Test
    void invalidTransitionThrowsAndSavesNothing() {
        when(parcelRepository.findById(PARCEL_ID)).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> service.updateStatus(PARCEL_ID, ParcelStatus.DELIVERED, null, COURIER_ID))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void unknownParcelThrowsNotFound() {
        when(parcelRepository.findById(PARCEL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(PARCEL_ID, ParcelStatus.PICKED_UP, null, COURIER_ID))
                .isInstanceOf(ParcelNotFoundException.class)
                .hasMessageContaining(PARCEL_ID.toString());

        verify(historyRepository, never()).save(any());
    }

    @Test
    void blankNoteIsStoredAsNull() {
        when(parcelRepository.findById(PARCEL_ID)).thenReturn(Optional.of(parcel));

        service.updateStatus(PARCEL_ID, ParcelStatus.PICKED_UP, "   ", COURIER_ID);

        ArgumentCaptor<ParcelStatusHistory> history = ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getNote()).isNull();
    }

    @Test
    void recordCreationAddsFirstTimelineEntryAndAnnouncesIt() {
        service.recordCreation(parcel, null);

        ArgumentCaptor<ParcelStatusHistory> history = ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isNull();
        assertThat(history.getValue().getToStatus()).isEqualTo(ParcelStatus.CREATED);

        ArgumentCaptor<ParcelStatusChangedEvent> event = ArgumentCaptor.forClass(ParcelStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().oldStatus()).isNull();
        assertThat(event.getValue().newStatus()).isEqualTo(ParcelStatus.CREATED);
    }
}
