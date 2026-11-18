package com.swiftdrop.backend.dev;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Local development only: adds a few demo parcels (once) so the tracking pages
 * have something to show. Uses the real status service, so the history and
 * events are exactly what production would produce.
 */
@Slf4j
@Profile(DevProfile.NAME)
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final ParcelRepository parcelRepository;
    private final ParcelStatusService parcelStatusService;
    private final Clock clock;

    private record DemoParcel(String trackingNumber, String recipient, String dropoffAddress, String dropoffCity,
                              ServiceType serviceType, String weightKg, String fee, ParcelStatus reachStatus) {
    }

    private static final List<DemoParcel> DEMO_PARCELS = List.of(
            new DemoParcel("SDP7845621", "Nimal Silva", "No. 12, Galle Road", "Dehiwala",
                    ServiceType.STANDARD, "5.00", "590.00", ParcelStatus.IN_TRANSIT),
            new DemoParcel("SDP7845638", "Kamala Jayasuriya", "No. 8, Peradeniya Road", "Kandy",
                    ServiceType.EXPRESS, "1.20", "1490.00", ParcelStatus.OUT_FOR_DELIVERY),
            new DemoParcel("SDP7845009", "Ruwan Fernando", "No. 44, Lighthouse Street", "Galle",
                    ServiceType.STANDARD, "3.40", "890.00", ParcelStatus.OUT_FOR_DELIVERY));

    @Override
    public void run(ApplicationArguments args) {
        DEMO_PARCELS.stream()
                .filter(demo -> parcelRepository.findByTrackingNumber(demo.trackingNumber()).isEmpty())
                .forEach(this::seed);
    }

    private void seed(DemoParcel demo) {
        Parcel parcel = parcelRepository.save(Parcel.builder()
                .trackingNumber(demo.trackingNumber())
                .senderName("Sanuja Perera")
                .recipientName(demo.recipient())
                .pickupAddress("No. 415, Cotta Road")
                .pickupCity("Colombo")
                .dropoffAddress(demo.dropoffAddress())
                .dropoffCity(demo.dropoffCity())
                .serviceType(demo.serviceType())
                .weightKg(new BigDecimal(demo.weightKg()))
                .fee(new BigDecimal(demo.fee()))
                .estimatedDeliveryAt(clock.instant().plus(Duration.ofDays(1)))
                .build());
        parcelStatusService.recordCreation(parcel, null);

        ParcelStatus current = ParcelStatus.CREATED;
        while (current != demo.reachStatus()) {
            current = nextOnHappyPath(current);
            parcelStatusService.updateStatus(parcel.getId(), current, null, null);
        }
        log.info("Seeded demo parcel {} ({})", demo.trackingNumber(), demo.reachStatus());
    }

    /** Follows the successful delivery route; FAILED is never chosen automatically. */
    private static ParcelStatus nextOnHappyPath(ParcelStatus status) {
        return status.nextStatuses().stream()
                .filter(next -> next != ParcelStatus.FAILED)
                .findFirst()
                .orElseThrow();
    }
}
