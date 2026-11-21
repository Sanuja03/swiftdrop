package com.swiftdrop.backend.modules.reports.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.dev.DemoDataSeeder;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.courier.entity.DeliveryProof;
import com.swiftdrop.backend.modules.courier.repository.DeliveryProofRepository;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

/** Uses the real demo seeder and report queries, isolated from Supabase, mail and RabbitMQ. */
@SpringBootTest(properties = {"app.messaging.outbox.publisher-enabled=false", "management.health.mail.enabled=false"})
@Transactional
class DemoReportAuditTest {
    @Autowired ParcelRepository parcels;
    @Autowired ParcelStatusService statuses;
    @Autowired ZoneRepository zones;
    @Autowired PricingRuleRepository prices;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @Autowired ReportService reports;
    @Autowired DeliveryProofRepository proofs;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;

    private void seed() {
        var seeder = new DemoDataSeeder(parcels, statuses, zones, prices, users, encoder, clock);
        ReflectionTestUtils.setField(seeder, "configuredPassword", "isolated-test-only-password");
        seeder.run(null);
        parcels.flush();
    }

    @Test
    void actualDemoDataMatchesOverviewAndSeedingTwiceDoesNotInflateReports() {
        seed();
        seed();
        var summary = reports.getReportSummary();
        assertEquals(3L, reports.getTotalParcels());
        assertEquals(3L, summary.totalParcels());
        assertEquals(1L, summary.inTransitParcels());
        assertEquals(1L, summary.outForDeliveryParcels());
        assertEquals(0L, summary.deliveredParcels());
        assertEquals(0L, summary.failedParcels());
        assertEquals(0, new BigDecimal("2970.00").compareTo(summary.totalFees()));
        assertEquals(0.0, reports.getDeliverySuccessRate().successRate());
        assertEquals(0.0, reports.getDeliverySuccessRate().failureRate());
        var day = LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Colombo")));
        assertTrue(reports.getDailyDeliveries(day, day).isEmpty());
        verifyNoInteractions(mail);
    }

    @Test
    void demoDeliveriesAndFailuresProduceExpectedDailyCountsAndRates() {
        seed();
        var delivered = parcels.findByTrackingNumber("SDP7845638").orElseThrow();
        var failed = parcels.findByTrackingNumber("SDP7845621").orElseThrow();
        var courier = users.findByEmail(DemoDataSeeder.COURIER_EMAIL).orElseThrow();
        proofs.saveAndFlush(new DeliveryProof(delivered.getId(), courier.getId(), "Test Recipient Two",
                null, clock.instant(), null, null));
        statuses.updateStatus(delivered.getId(), ParcelStatus.DELIVERED, null, courier.getId());
        statuses.updateStatus(failed.getId(), ParcelStatus.OUT_FOR_DELIVERY, null, courier.getId());
        statuses.updateStatus(failed.getId(), ParcelStatus.FAILED, "Isolated audit delivery attempt", courier.getId());
        parcels.flush();
        var day = LocalDate.of(2026, 11, 21);
        // Fix the history date explicitly: assertions are independent of the wall clock and CI timezone.
        jdbc.update("UPDATE parcel_status_history SET changed_at = CAST(? AS TIMESTAMP WITH TIME ZONE)",
                "2026-11-21T18:29:59Z");
        var summary = reports.getReportSummary();
        assertEquals(1L, summary.deliveredParcels());
        assertEquals(1L, summary.failedParcels());
        assertEquals(0, new BigDecimal("2970.00").compareTo(summary.totalFees()));
        var rates = reports.getDeliverySuccessRate(day, day);
        assertEquals(1L, rates.deliveredParcels());
        assertEquals(1L, rates.failedParcels());
        assertEquals(50.0, rates.successRate());
        assertEquals(50.0, rates.failureRate());
        var daily = reports.getDailyDeliveries(day, day);
        assertEquals(1, daily.size());
        assertEquals(1L, daily.getFirst().deliveries());
        assertTrue(reports.getDailyDeliveries(day.plusDays(1), day.plusDays(1)).isEmpty());
        verifyNoInteractions(mail);
    }
}
