package com.swiftdrop.backend.modules.assistant.service;

import com.swiftdrop.backend.modules.assistant.dto.CustomerParcelSummary;
import com.swiftdrop.backend.modules.assistant.dto.EstimateFeeToolRequest;
import com.swiftdrop.backend.modules.assistant.dto.EstimateFeeToolResult;
import com.swiftdrop.backend.modules.assistant.dto.TrackParcelResult;
import com.swiftdrop.backend.modules.assistant.service.impl.AssistantToolServiceImpl;
import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.pricing.dto.EstimateRequest;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.service.PricingService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.dto.PublicTrackingResponse;
import com.swiftdrop.backend.modules.tracking.dto.TrackingEventResponse;
import com.swiftdrop.backend.modules.tracking.service.TrackingQueryService;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantToolServiceImplTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID COLOMBO_ZONE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID KANDY_ZONE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private TrackingQueryService trackingQueryService;

    @Mock
    private PricingService pricingService;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private UserRepository userRepository;

    private AssistantToolServiceImpl assistantToolService;

    private Zone colomboZone;
    private Zone kandyZone;
    private User activeCustomer;

    @BeforeEach
    void setUp() {
        assistantToolService = new AssistantToolServiceImpl(
                trackingQueryService,
                pricingService,
                zoneRepository,
                parcelRepository,
                userRepository
        );

        colomboZone = mock(Zone.class);
        lenient().when(colomboZone.getId()).thenReturn(COLOMBO_ZONE_ID);
        lenient().when(colomboZone.getName()).thenReturn("Colombo");
        lenient().when(colomboZone.getCode()).thenReturn("COLOMBO");

        kandyZone = mock(Zone.class);
        lenient().when(kandyZone.getId()).thenReturn(KANDY_ZONE_ID);
        lenient().when(kandyZone.getName()).thenReturn("Kandy");
        lenient().when(kandyZone.getCode()).thenReturn("KANDY");

        activeCustomer = User.builder()
                .id(CUSTOMER_ID)
                .email("customer@swiftdrop.lk")
                .fullName("Kamal Perera")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // track_parcel tests
    // =========================================================================

    @Test
    @DisplayName("trackParcel returns sanitized tracking details when parcel exists")
    void trackParcel_success_returnsSanitizedTrackingDetails() {
        String trackingNumber = "SDP1234567";
        Instant deliveryEta = Instant.now().plusSeconds(86400);
        Instant updatedAt = Instant.now();

        TrackingEventResponse event = new TrackingEventResponse(
                ParcelStatus.IN_TRANSIT,
                "In Transit",
                updatedAt
        );

        PublicTrackingResponse response = new PublicTrackingResponse(
                trackingNumber,
                ParcelStatus.IN_TRANSIT,
                "In Transit",
                "Colombo",
                "Kandy",
                ServiceType.STANDARD,
                "Standard (1-2 days)",
                deliveryEta,
                updatedAt,
                List.of(event)
        );

        when(trackingQueryService.getPublicTracking(trackingNumber)).thenReturn(response);

        TrackParcelResult result = assistantToolService.trackParcel(trackingNumber);

        assertThat(result.isFound()).isTrue();
        assertThat(result.getTrackingNumber()).isEqualTo(trackingNumber);
        assertThat(result.getStatus()).isEqualTo("IN_TRANSIT");
        assertThat(result.getStatusLabel()).isEqualTo("In Transit");
        assertThat(result.getOriginCity()).isEqualTo("Colombo");
        assertThat(result.getDestinationCity()).isEqualTo("Kandy");
        assertThat(result.getServiceLabel()).isEqualTo("Standard (1-2 days)");
        assertThat(result.getEstimatedDeliveryAt()).isEqualTo(deliveryEta);
        assertThat(result.getTimeline()).hasSize(1);
        assertThat(result.getMessage()).contains("SDP1234567").contains("In Transit");

        verify(trackingQueryService).getPublicTracking(trackingNumber);
    }

    @Test
    @DisplayName("trackParcel handles ParcelNotFoundException safely without leaking stack trace")
    void trackParcel_notFound_returnsSafeFailureResult() {
        String trackingNumber = "UNKNOWN999";
        when(trackingQueryService.getPublicTracking(trackingNumber))
                .thenThrow(new ParcelNotFoundException(trackingNumber));

        TrackParcelResult result = assistantToolService.trackParcel(trackingNumber);

        assertThat(result.isFound()).isFalse();
        assertThat(result.getTrackingNumber()).isEqualTo(trackingNumber);
        assertThat(result.getMessage()).contains("No parcel found matching tracking number UNKNOWN999");
    }

    @Test
    @DisplayName("trackParcel rejects null or blank tracking number with validation message")
    void trackParcel_nullOrBlank_returnsValidationFailure() {
        TrackParcelResult nullResult = assistantToolService.trackParcel(null);
        assertThat(nullResult.isFound()).isFalse();
        assertThat(nullResult.getMessage()).contains("Please provide a valid parcel tracking number");

        TrackParcelResult blankResult = assistantToolService.trackParcel("   ");
        assertThat(blankResult.isFound()).isFalse();
        assertThat(blankResult.getMessage()).contains("Please provide a valid parcel tracking number");

        verifyNoInteractions(trackingQueryService);
    }

    @Test
    @DisplayName("trackParcel handles unexpected internal exception without leaking details")
    void trackParcel_unexpectedException_returnsSafeErrorMessage() {
        when(trackingQueryService.getPublicTracking("ERR123"))
                .thenThrow(new RuntimeException("Database connection timeout"));

        TrackParcelResult result = assistantToolService.trackParcel("ERR123");

        assertThat(result.isFound()).isFalse();
        assertThat(result.getMessage()).contains("Unable to retrieve tracking information at this time");
        assertThat(result.getMessage()).doesNotContain("Database connection timeout");
    }

    // =========================================================================
    // estimate_fee tests
    // =========================================================================

    @Test
    @DisplayName("estimateFee successfully calculates delivery fee reusing PricingService")
    void estimateFee_success_calculatesFeeAndReturnsBreakdown() {
        when(zoneRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(colomboZone, kandyZone));

        FeeBreakdown feeBreakdown = new FeeBreakdown(
                new BigDecimal("590.00"),
                new BigDecimal("200.00"),
                new BigDecimal("250.00"),
                new BigDecimal("1040.00"),
                "LKR",
                Instant.now().plusSeconds(86400)
        );

        when(pricingService.estimate(any(EstimateRequest.class))).thenReturn(feeBreakdown);

        EstimateFeeToolRequest request = EstimateFeeToolRequest.builder()
                .pickupCity("Colombo")
                .dropoffCity("Kandy")
                .weightKg(new BigDecimal("2.0"))
                .serviceType("STANDARD")
                .build();

        EstimateFeeToolResult result = assistantToolService.estimateFee(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalFee()).isEqualTo(new BigDecimal("1040.00"));
        assertThat(result.getBaseFee()).isEqualTo(new BigDecimal("590.00"));
        assertThat(result.getWeightCharge()).isEqualTo(new BigDecimal("200.00"));
        assertThat(result.getZoneCharge()).isEqualTo(new BigDecimal("250.00"));
        assertThat(result.getCurrency()).isEqualTo("LKR");
        assertThat(result.getPickupZone()).isEqualTo("Colombo");
        assertThat(result.getDropoffZone()).isEqualTo("Kandy");
        assertThat(result.getParcelSize()).isEqualTo("SMALL");
        assertThat(result.getMessage()).contains("1040.00").contains("LKR");

        ArgumentCaptor<EstimateRequest> captor = ArgumentCaptor.forClass(EstimateRequest.class);
        verify(pricingService).estimate(captor.capture());
        EstimateRequest actualRequest = captor.getValue();
        assertThat(actualRequest.pickupZoneId()).isEqualTo(COLOMBO_ZONE_ID);
        assertThat(actualRequest.dropoffZoneId()).isEqualTo(KANDY_ZONE_ID);
        assertThat(actualRequest.parcelSize()).isEqualTo(ParcelSize.SMALL);
        assertThat(actualRequest.serviceType()).isEqualTo(ServiceType.STANDARD);
    }

    @Test
    @DisplayName("estimateFee detects missing pickup city and asks follow-up")
    void estimateFee_missingPickupCity_returnsMissingFieldsResult() {
        when(zoneRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(colomboZone, kandyZone));

        EstimateFeeToolRequest request = EstimateFeeToolRequest.builder()
                .dropoffCity("Kandy")
                .weightKg(new BigDecimal("2.0"))
                .build();

        EstimateFeeToolResult result = assistantToolService.estimateFee(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMissingFields()).contains("pickupCity");
        assertThat(result.getMessage()).contains("Missing required pricing input");
        assertThat(result.getAvailableZones()).contains("Colombo", "Kandy");
        verifyNoInteractions(pricingService);
    }

    @Test
    @DisplayName("estimateFee detects missing dropoff city and weight")
    void estimateFee_missingDropoffCityAndWeight_returnsMissingFieldsResult() {
        when(zoneRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(colomboZone, kandyZone));

        EstimateFeeToolRequest request = EstimateFeeToolRequest.builder()
                .pickupCity("Colombo")
                .build();

        EstimateFeeToolResult result = assistantToolService.estimateFee(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMissingFields()).contains("dropoffCity", "weightKg");
        verifyNoInteractions(pricingService);
    }

    @Test
    @DisplayName("estimateFee rejects negative or zero weight")
    void estimateFee_invalidWeight_returnsValidationError() {
        when(zoneRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(colomboZone, kandyZone));

        EstimateFeeToolRequest zeroWeight = EstimateFeeToolRequest.builder()
                .pickupCity("Colombo")
                .dropoffCity("Kandy")
                .weightKg(BigDecimal.ZERO)
                .build();

        EstimateFeeToolResult zeroResult = assistantToolService.estimateFee(zeroWeight);
        assertThat(zeroResult.isSuccess()).isFalse();
        assertThat(zeroResult.getMessage()).contains("must be greater than 0 kg");

        EstimateFeeToolRequest negWeight = EstimateFeeToolRequest.builder()
                .pickupCity("Colombo")
                .dropoffCity("Kandy")
                .weightKg(new BigDecimal("-1.5"))
                .build();

        EstimateFeeToolResult negResult = assistantToolService.estimateFee(negWeight);
        assertThat(negResult.isSuccess()).isFalse();
        assertThat(negResult.getMessage()).contains("must be greater than 0 kg");

        verifyNoInteractions(pricingService);
    }

    @Test
    @DisplayName("estimateFee rejects weight exceeding maximum 30 kg")
    void estimateFee_weightExceedsMax_returnsValidationError() {
        when(zoneRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(colomboZone, kandyZone));

        EstimateFeeToolRequest request = EstimateFeeToolRequest.builder()
                .pickupCity("Colombo")
                .dropoffCity("Kandy")
                .weightKg(new BigDecimal("35.0"))
                .build();

        EstimateFeeToolResult result = assistantToolService.estimateFee(request);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Maximum parcel weight is 30 kg");
        verifyNoInteractions(pricingService);
    }

    @Test
    @DisplayName("estimateFee reports unknown pickup or destination city with supported zones")
    void estimateFee_unknownZone_returnsAvailableZones() {
        when(zoneRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(colomboZone, kandyZone));

        EstimateFeeToolRequest request = EstimateFeeToolRequest.builder()
                .pickupCity("London")
                .dropoffCity("Kandy")
                .weightKg(new BigDecimal("1.5"))
                .build();

        EstimateFeeToolResult result = assistantToolService.estimateFee(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Unknown pickup city: 'London'");
        assertThat(result.getAvailableZones()).contains("Colombo", "Kandy");
        verifyNoInteractions(pricingService);
    }

    @Test
    @DisplayName("estimateFee handles PricingException cleanly without throwing 500")
    void estimateFee_pricingException_returnsSafeFailureMessage() {
        when(zoneRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(colomboZone, kandyZone));
        when(pricingService.estimate(any())).thenThrow(PricingException.inactiveRule(ServiceType.STANDARD));

        EstimateFeeToolRequest request = EstimateFeeToolRequest.builder()
                .pickupCity("Colombo")
                .dropoffCity("Kandy")
                .weightKg(new BigDecimal("2.0"))
                .build();

        EstimateFeeToolResult result = assistantToolService.estimateFee(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Could not calculate fee");
    }

    // =========================================================================
    // list_my_parcels tests
    // =========================================================================

    @Test
    @DisplayName("listMyParcels returns parcels belonging to authenticated customer")
    void listMyParcels_authenticatedCustomer_returnsCustomerParcels() {
        // Setup SecurityContext with authenticated customer email
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer@swiftdrop.lk",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findByEmail("customer@swiftdrop.lk")).thenReturn(Optional.of(activeCustomer));

        Parcel p1 = Parcel.builder()
                .trackingNumber("SDP1000001")
                .customerId(CUSTOMER_ID)
                .senderName("Kamal Perera")
                .recipientName("Nimal Silva")
                .pickupAddress("10 Secret Street")
                .pickupCity("Colombo")
                .dropoffAddress("20 Hidden Lane")
                .dropoffCity("Kandy")
                .recipientPhone("0712345678")
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("1.5"))
                .fee(new BigDecimal("840.00"))
                .status(ParcelStatus.IN_TRANSIT)
                .build();

        when(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of(p1));

        List<CustomerParcelSummary> summaries = assistantToolService.listMyParcels();

        assertThat(summaries).hasSize(1);
        CustomerParcelSummary summary = summaries.get(0);
        assertThat(summary.getTrackingNumber()).isEqualTo("SDP1000001");
        assertThat(summary.getStatus()).isEqualTo("IN_TRANSIT");
        assertThat(summary.getDropoffCity()).isEqualTo("Kandy");
        assertThat(summary.getServiceType()).isEqualTo("STANDARD");
        assertThat(summary.getFee()).isEqualTo(new BigDecimal("840.00"));

        verify(parcelRepository).findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID);
    }

    @Test
    @DisplayName("listMyParcels returns empty list when customer has no parcels")
    void listMyParcels_customerWithNoParcels_returnsEmptyList() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer@swiftdrop.lk",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findByEmail("customer@swiftdrop.lk")).thenReturn(Optional.of(activeCustomer));
        when(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(Collections.emptyList());

        List<CustomerParcelSummary> summaries = assistantToolService.listMyParcels();

        assertThat(summaries).isEmpty();
        verify(parcelRepository).findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID);
    }

    @Test
    @DisplayName("listMyParcels rejects unauthenticated request when SecurityContext is empty")
    void listMyParcels_unauthenticated_throwsAccessDeniedException() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> assistantToolService.listMyParcels())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");

        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("listMyParcels rejects anonymous user authentication")
    void listMyParcels_anonymousUser_throwsAccessDeniedException() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        assertThatThrownBy(() -> assistantToolService.listMyParcels())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");

        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("listMyParcels rejects inactive customer account")
    void listMyParcels_inactiveUser_throwsAccessDeniedException() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer@swiftdrop.lk",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        User inactiveCustomer = User.builder()
                .id(CUSTOMER_ID)
                .email("customer@swiftdrop.lk")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.SUSPENDED)
                .build();

        when(userRepository.findByEmail("customer@swiftdrop.lk")).thenReturn(Optional.of(inactiveCustomer));

        assertThatThrownBy(() -> assistantToolService.listMyParcels())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not active");

        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("listMyParcels(customerId) rejects null customerId")
    void listMyParcels_nullCustomerId_throwsAccessDeniedException() {
        assertThatThrownBy(() -> assistantToolService.listMyParcels(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");

        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("listMyParcels strictly scopes queries to authenticated customer and never other customers")
    void listMyParcels_neverReturnsOtherCustomerParcels() {
        when(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(Collections.emptyList());

        assistantToolService.listMyParcels(CUSTOMER_ID);

        verify(parcelRepository).findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID);
        verify(parcelRepository, never()).findByCustomerIdOrderByCreatedAtDesc(OTHER_CUSTOMER_ID);
    }
}
