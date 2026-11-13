package com.swiftdrop.backend.support;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;

/**
 * Builds parcels for tests so each test does not repeat the same setup.
 */
public final class TestParcels {

    public static final String TRACKING_NUMBER = "SDP7845621";

    private TestParcels() {
    }

    public static Parcel withId(UUID id) {
        Parcel parcel = Parcel.builder()
                .trackingNumber(TRACKING_NUMBER)
                .senderName("Sanuja Perera")
                .recipientName("Nimal Silva")
                .pickupAddress("No. 415, Cotta Road")
                .pickupCity("Colombo")
                .dropoffAddress("No. 12, Galle Road")
                .dropoffCity("Dehiwala")
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("2.50"))
                .fee(new BigDecimal("590.00"))
                .build();
        ReflectionTestUtils.setField(parcel, "id", id);
        return parcel;
    }
}
