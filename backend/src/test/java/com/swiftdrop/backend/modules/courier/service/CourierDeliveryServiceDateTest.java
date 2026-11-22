package com.swiftdrop.backend.modules.courier.service;

import com.swiftdrop.backend.modules.courier.repository.CourierAssignmentRepository;
import com.swiftdrop.backend.modules.courier.repository.DeliveryProofRepository;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourierDeliveryServiceDateTest {

    @ParameterizedTest
    @CsvSource({
            "2026-11-21T18:29:59Z, 2026-11-21",
            "2026-11-21T18:30:00Z, 2026-11-22"
    })
    void todayChangesAtColomboMidnight(String timestamp, LocalDate expectedDate) {
        var couriers = mock(CourierService.class);
        var assignments = mock(CourierAssignmentRepository.class);
        Principal principal = () -> "courier@example.test";
        UUID courierId = UUID.randomUUID();
        when(couriers.requireCurrentCourierUserId(principal)).thenReturn(courierId);
        when(assignments.findToday(courierId, expectedDate)).thenReturn(List.of());
        var service = new CourierDeliveryService(couriers, assignments, mock(DeliveryProofRepository.class),
                mock(ParcelRepository.class), mock(ParcelStatusService.class),
                Clock.fixed(Instant.parse(timestamp), ZoneOffset.UTC));

        assertThat(service.today(principal)).isEmpty();
        verify(assignments).findToday(courierId, expectedDate);
    }
}
