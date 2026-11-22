package com.swiftdrop.backend.modules.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.support.TestParcels;

class CustomerShipmentQueryServiceTest {
    private static final UUID CUSTOMER_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");

    private final ParcelRepository parcelRepository = mock(ParcelRepository.class);
    private final CustomerShipmentQueryService service = new CustomerShipmentQueryService(parcelRepository);

    @Test
    void returnsOnlyTheCustomersParcelsAsSummaries() {
        var parcel = TestParcels.withId(UUID.randomUUID());
        when(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of(parcel));

        var shipments = service.getShipments(CUSTOMER_ID);

        assertThat(shipments).hasSize(1);
        var shipment = shipments.getFirst();
        assertThat(shipment.trackingNumber()).isEqualTo(parcel.getTrackingNumber());
        assertThat(shipment.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(shipment.statusLabel()).isEqualTo(ParcelStatus.CREATED.getLabel());
        assertThat(shipment.originCity()).isEqualTo(parcel.getPickupCity());
        assertThat(shipment.destinationCity()).isEqualTo(parcel.getDropoffCity());
        assertThat(shipment.serviceType()).isEqualTo(ServiceType.STANDARD);
        assertThat(shipment.serviceLabel()).isEqualTo(ServiceType.STANDARD.getLabel());
    }

    @Test
    void customerWithoutParcelsGetsAnEmptyList() {
        when(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of());

        assertThat(service.getShipments(CUSTOMER_ID)).isEmpty();
    }
}
