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
import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Local development only: adds default zones, pricing rules, and a few demo parcels (once)
 * so the tracking and merchant pricing pages have data to show.
 */
@Slf4j
@Profile(DevProfile.NAME)
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final ParcelRepository parcelRepository;
    private final ParcelStatusService parcelStatusService;
    private final ZoneRepository zoneRepository;
    private final PricingRuleRepository pricingRuleRepository;
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
        if (zoneRepository.count() == 0) {
            zoneRepository.saveAll(List.of(
                    Zone.create("COLOMBO", "Colombo", BigDecimal.ZERO),
                    Zone.create("GAMPAHA", "Gampaha", BigDecimal.ZERO),
                    Zone.create("KALUTARA", "Kalutara", BigDecimal.ZERO),
                    Zone.create("KANDY", "Kandy", new BigDecimal("150")),
                    Zone.create("GALLE", "Galle", new BigDecimal("150")),
                    Zone.create("MATARA", "Matara", new BigDecimal("150")),
                    Zone.create("KURUNEGALA", "Kurunegala", new BigDecimal("150")),
                    Zone.create("JAFFNA", "Jaffna", new BigDecimal("350"))
            ));
            log.info("Seeded default zones for dev profile");
        }

        if (pricingRuleRepository.count() == 0) {
            pricingRuleRepository.saveAll(List.of(
                    PricingRule.create(ServiceType.STANDARD, new BigDecimal("590"), new BigDecimal("100"), new BigDecimal("250")),
                    PricingRule.create(ServiceType.EXPRESS, new BigDecimal("990"), new BigDecimal("100"), new BigDecimal("400")),
                    PricingRule.create(ServiceType.SAME_DAY, new BigDecimal("1490"), new BigDecimal("100"), new BigDecimal("600"))
            ));
            log.info("Seeded default pricing rules for dev profile");
        }

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
