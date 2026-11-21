package com.swiftdrop.backend.dev;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.AuthProvider;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
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
 * Local development only: adds default zones, pricing rules, one test account per staff role,
 * a test customer and a few demo parcels owned by that customer (each once), so every role's
 * pages have data to show.
 */
@Slf4j
@Profile(DevProfile.NAME)
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    public static final String CUSTOMER_EMAIL = "test.customer@example.com";
    public static final String COURIER_EMAIL = "test.courier@example.com";
    public static final String DISPATCHER_EMAIL = "test.dispatcher@example.com";
    public static final String ADMIN_EMAIL = "test.admin@example.com";

    /** The order every successful delivery follows. CANCELLED and FAILED are never chosen here. */
    static final List<ParcelStatus> HAPPY_PATH = List.of(
            ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);

    private final ParcelRepository parcelRepository;
    private final ParcelStatusService parcelStatusService;
    private final ZoneRepository zoneRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${DEV_USER_PASSWORD:}")
    private String configuredPassword;

    private record DemoParcel(String trackingNumber, String recipient, String dropoffAddress, String dropoffCity,
                              ServiceType serviceType, String weightKg, String fee, ParcelStatus reachStatus) {
    }

    private static final List<DemoParcel> DEMO_PARCELS = List.of(
            new DemoParcel("SDP7845621", "Test Recipient One", "No. 1, Test Road", "Dehiwala",
                    ServiceType.STANDARD, "5.00", "590.00", ParcelStatus.IN_TRANSIT),
            new DemoParcel("SDP7845638", "Test Recipient Two", "No. 2, Test Road", "Kandy",
                    ServiceType.EXPRESS, "1.20", "1490.00", ParcelStatus.OUT_FOR_DELIVERY),
            new DemoParcel("SDP7845009", "Test Recipient Three", "No. 3, Test Road", "Galle",
                    ServiceType.STANDARD, "3.40", "890.00", ParcelStatus.CREATED));

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

        User customer = ensureUser(CUSTOMER_EMAIL, "Test Customer", Role.ROLE_CUSTOMER);
        ensureUser(COURIER_EMAIL, "Test Courier", Role.ROLE_COURIER);
        ensureUser(DISPATCHER_EMAIL, "Test Dispatcher", Role.ROLE_DISPATCHER);
        ensureUser(ADMIN_EMAIL, "Test Admin", Role.ROLE_ADMIN);

        DEMO_PARCELS.stream()
                .filter(demo -> parcelRepository.findByTrackingNumber(demo.trackingNumber()).isEmpty())
                .forEach(demo -> seed(demo, customer));
    }

    private User ensureUser(String email, String fullName, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            boolean generated = configuredPassword == null || configuredPassword.isBlank();
            String password = generated ? randomPassword() : configuredPassword;
            User user = userRepository.save(User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .fullName(fullName)
                    .role(role)
                    .status(AccountStatus.ACTIVE)
                    .provider(AuthProvider.LOCAL)
                    .emailVerified(true)
                    .build());
            if (generated) {
                log.info("Dev {} account {} created with generated password: {} (set DEV_USER_PASSWORD to choose one)",
                        role, email, password);
            } else {
                log.info("Dev {} account {} created with DEV_USER_PASSWORD", role, email);
            }
            return user;
        });
    }

    private void seed(DemoParcel demo, User customer) {
        Parcel parcel = parcelRepository.save(Parcel.builder()
                .trackingNumber(demo.trackingNumber())
                .customerId(customer.getId())
                .senderName(customer.getFullName())
                .recipientName(demo.recipient())
                .pickupAddress("No. 10, Sample Street")
                .pickupCity("Colombo")
                .dropoffAddress(demo.dropoffAddress())
                .dropoffCity(demo.dropoffCity())
                .serviceType(demo.serviceType())
                .weightKg(new BigDecimal(demo.weightKg()))
                .fee(new BigDecimal(demo.fee()))
                .estimatedDeliveryAt(clock.instant().plus(Duration.ofDays(1)))
                .build());
        parcelStatusService.recordCreation(parcel, customer.getId());

        for (ParcelStatus next : stepsTo(demo.reachStatus())) {
            parcelStatusService.updateStatus(parcel.getId(), next, null, null);
        }
        log.info("Seeded demo parcel {} ({})", demo.trackingNumber(), demo.reachStatus());
    }

    /** The statuses after CREATED, in order, up to and including {@code target}. */
    static List<ParcelStatus> stepsTo(ParcelStatus target) {
        int index = HAPPY_PATH.indexOf(target);
        if (index < 0) {
            throw new IllegalArgumentException("Demo parcels can only reach a status on the happy path: " + target);
        }
        return HAPPY_PATH.subList(1, index + 1);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
