package com.swiftdrop.backend.modules.courier.service;

import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.courier.dto.CourierDeliveryResponse;
import com.swiftdrop.backend.modules.courier.entity.Courier;
import com.swiftdrop.backend.modules.courier.repository.CourierRepository;
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
    @Autowired CourierRepository couriers;
    @Autowired ParcelRepository parcels;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean ProofPhotoStorage photos;

    private final Principal courier = () -> "first@courier.test";
    private final Principal other = () -> "second@courier.test";
    private UUID parcelId;

    @BeforeEach
    void setUp() {
        UUID courierId = createCourier(courier);
        UUID otherId = createCourier(other);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Colombo")));
        parcelId = assignedParcel(courierId, today, "SDP-TODAY");
        assignedParcel(courierId, today.minusDays(1), "SDP-OVERDUE");
        assignedParcel(otherId, today, "SDP-OTHER");
    }

    @Test
    void onlyShowsOwnDeliveriesScheduledForToday() {
        assertThat(deliveries.today(courier)).extracting(CourierDeliveryResponse::parcelId)
                .containsExactly(parcelId);
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
        var initial = proofs.save(parcelId, courier, "Original name", null, null);
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
        return couriers.save(new Courier(user.getId())).getId();
    }

    private UUID assignedParcel(UUID courierId, LocalDate date, String trackingNumber) {
        var parcel = TestParcels.withId(null);
        org.springframework.test.util.ReflectionTestUtils.setField(parcel, "trackingNumber", trackingNumber);
        UUID id = parcels.saveAndFlush(parcel).getId();
        jdbc.update("insert into courier_assignments (parcel_id, courier_id, scheduled_date) values (?, ?, ?)",
                id, courierId, java.sql.Date.valueOf(date));
        return id;
    }
}
