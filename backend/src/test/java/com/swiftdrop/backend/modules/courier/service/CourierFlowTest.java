package com.swiftdrop.backend.modules.courier.service;

import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.courier.dto.CourierDeliveryResponse;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.exception.ParcelStatusRequirementException;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.support.TestParcels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class CourierFlowTest {

    @Autowired CourierDeliveryService deliveries;
    @Autowired DeliveryProofService proofs;
    @Autowired ParcelStatusService statuses;
    @Autowired UserRepository users;
    @Autowired ParcelRepository parcels;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean ProofPhotoStorage photos;

    private final Principal courier = () -> "first@courier.test";
    private final Principal other = () -> "second@courier.test";
    private UUID parcelId;
    private UUID courierUserId;
    private UUID dispatcherId;

    @BeforeEach
    void setUp() {
        // Hibernate creates only the courier read view's columns. Include the shared
        // dispatch columns and constraints so JDBC fixtures match schema.sql too.
        jdbc.execute("alter table courier_assignments add column if not exists dispatcher_id UUID not null references users(id)");
        jdbc.execute("alter table courier_assignments add column if not exists assigned_at timestamp with time zone default current_timestamp not null");
        jdbc.execute("alter table courier_assignments add column if not exists updated_at timestamp with time zone default current_timestamp not null");
        jdbc.execute("alter table courier_assignments add column if not exists version bigint default 0 not null");
        dispatcherId = users.saveAndFlush(User.builder().email("dispatcher@courier.test")
                .fullName("Test dispatcher").role(Role.ROLE_DISPATCHER).build()).getId();
        courierUserId = createCourier(courier);
        UUID otherId = createCourier(other);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Colombo")));
        parcelId = assignedParcel(courierUserId, today, "SDP-TODAY");
        assignedParcel(courierUserId, today.minusDays(1), "SDP-OVERDUE");
        assignedParcel(courierUserId, today.plusDays(1), "SDP-TOMORROW");
        assignedParcel(courierUserId, null, "SDP-NO-ESTIMATE");
        assignedParcel(otherId, today, "SDP-OTHER");
        jdbc.update("insert into courier_assignments (id, parcel_id, courier_id, dispatcher_id, status)"
                + " values (?, ?, ?, ?, 'CANCELLED')", UUID.randomUUID(), parcelId, otherId, dispatcherId);
    }

    @Test
    void onlyShowsOwnDeliveriesScheduledForToday() {
        assertThat(deliveries.today(courier)).extracting(CourierDeliveryResponse::parcelId)
                .containsExactly(parcelId);
    }

    @Test
    void todayExcludesAssignmentsWithAnyOtherStatus() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Colombo")));
        for (String status : new String[]{"ACCEPTED", "CANCELLED", "REJECTED", "COMPLETED"}) {
            UUID id = assignedParcel(courierUserId, today, "SDP-" + status);
            jdbc.update("update courier_assignments set status = ? where parcel_id = ?", status, id);
        }
        assertThat(deliveries.today(courier)).extracting(CourierDeliveryResponse::parcelId)
                .containsExactly(parcelId);
    }

    @Test
    void reassignedParcelOnlyAppearsForNewCourier() {
        jdbc.update("update courier_assignments set status = 'CANCELLED' where parcel_id = ?", parcelId);
        UUID otherId = users.findByEmail(other.getName()).orElseThrow().getId();
        jdbc.update("insert into courier_assignments (id, parcel_id, courier_id, dispatcher_id, status)"
                + " values (?, ?, ?, ?, 'ASSIGNED')", UUID.randomUUID(), parcelId, otherId, dispatcherId);

        assertThat(deliveries.today(courier)).isEmpty();
        assertThat(deliveries.today(other)).extracting(CourierDeliveryResponse::parcelId).contains(parcelId);
        assertThat(assertThrows(ResponseStatusException.class, () -> deliveries.details(parcelId, courier))
                .getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void anotherCourierCannotReadChangeOrSubmitProof() {
        assertThat(assertThrows(ResponseStatusException.class, () -> deliveries.details(parcelId, other))
                .getStatusCode().value()).isEqualTo(404);
        assertThat(assertThrows(ResponseStatusException.class,
                () -> deliveries.updateStatus(parcelId, ParcelStatus.PICKED_UP, null, other))
                .getStatusCode().value()).isEqualTo(404);
        assertThat(assertThrows(ResponseStatusException.class,
                () -> proofs.save(parcelId, other, "Recipient", null, null))
                .getStatusCode().value()).isEqualTo(404);
        assertThat(parcels.findById(parcelId).orElseThrow().getStatus()).isEqualTo(ParcelStatus.CREATED);
    }

    @Test
    void sharedStatusServiceRejectsDeliveredWithoutProof() {
        goOutForDelivery();
        assertThrows(ParcelStatusRequirementException.class,
                () -> statuses.updateStatus(parcelId, ParcelStatus.DELIVERED, null, null));
    }

    @Test
    void failedDeliveryRequiresReason() {
        goOutForDelivery();
        assertThrows(ParcelStatusRequirementException.class,
                () -> deliveries.updateStatus(parcelId, ParcelStatus.FAILED, " ", courier));
    }

    @Test
    void photoUploadFailureLeavesNoProofAndBlocksDelivery() {
        goOutForDelivery();
        var photo = new MockMultipartFile("photo", "proof.png", "image/png", new byte[]{1});
        when(photos.upload(parcelId, photo)).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Upload failed"));

        assertThrows(ResponseStatusException.class, () -> proofs.save(parcelId, courier, null, photo, null));
        assertThat(proofs.history(parcelId)).isEmpty();
        assertThrows(ResponseStatusException.class,
                () -> deliveries.updateStatus(parcelId, ParcelStatus.DELIVERED, null, courier));
    }

    @Test
    void proofPermitsDeliveryAndCorrectionKeepsOriginal() {
        goOutForDelivery();
        assertThat(deliveries.details(parcelId, courier).hasSavedProof()).isFalse();
        var initial = proofs.save(parcelId, courier, "Original name", null, null);
        assertThat(deliveries.details(parcelId, courier).hasSavedProof()).isTrue();
        assertThat(initial.courierId()).isEqualTo(courierUserId);
        deliveries.updateStatus(parcelId, ParcelStatus.DELIVERED, null, courier);
        var correction = proofs.save(parcelId, courier, "Corrected name", null, "Spelling");

        assertThat(deliveries.details(parcelId, courier).status()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(correction.previousProofId()).isEqualTo(initial.id());
        assertThat(proofs.history(parcelId)).extracting(proof -> proof.recipientName())
                .containsExactlyInAnyOrder("Original name", "Corrected name");
    }

    private void goOutForDelivery() {
        deliveries.updateStatus(parcelId, ParcelStatus.PICKED_UP, null, courier);
        deliveries.updateStatus(parcelId, ParcelStatus.IN_TRANSIT, null, courier);
        deliveries.updateStatus(parcelId, ParcelStatus.OUT_FOR_DELIVERY, null, courier);
    }

    private UUID createCourier(Principal principal) {
        User user = users.save(User.builder().email(principal.getName())
                .fullName("Test courier").role(Role.ROLE_COURIER).build());
        return user.getId();
    }

    private UUID assignedParcel(UUID courierId, LocalDate date, String trackingNumber) {
        var parcel = TestParcels.withId(null);
        org.springframework.test.util.ReflectionTestUtils.setField(parcel, "trackingNumber", trackingNumber);
        if (date != null) {
            org.springframework.test.util.ReflectionTestUtils.setField(parcel, "estimatedDeliveryAt",
                    date.atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant());
        }
        UUID id = parcels.saveAndFlush(parcel).getId();
        jdbc.update("insert into courier_assignments (id, parcel_id, courier_id, dispatcher_id, status)"
                + " values (?, ?, ?, ?, 'ASSIGNED')", UUID.randomUUID(), id, courierId, dispatcherId);
        return id;
    }
}
