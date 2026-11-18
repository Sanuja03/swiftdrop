package com.swiftdrop.backend.modules.dispatch.service;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.exception.AccountNotActiveException;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.dispatch.dto.AssignCourierRequest;
import com.swiftdrop.backend.modules.dispatch.dto.AssignmentResponse;
import com.swiftdrop.backend.modules.dispatch.dto.CourierWorkloadResponse;
import com.swiftdrop.backend.modules.dispatch.entity.AssignmentStatus;
import com.swiftdrop.backend.modules.dispatch.entity.CourierAssignment;
import com.swiftdrop.backend.modules.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.backend.modules.dispatch.exception.DuplicateAssignmentException;
import com.swiftdrop.backend.modules.dispatch.exception.InvalidAssignmentStateException;
import com.swiftdrop.backend.modules.dispatch.repository.DispatchCourierAssignmentRepository;
import com.swiftdrop.backend.modules.dispatch.service.impl.DispatchServiceImpl;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.support.TestParcels;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceImplTest {

    private static final UUID PARCEL_ID_1 = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PARCEL_ID_2 = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID PARCEL_ID_3 = UUID.fromString("10000000-0000-0000-0000-000000000003");

    private static final UUID COURIER_ID_1 = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID COURIER_ID_2 = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID COURIER_ID_3 = UUID.fromString("20000000-0000-0000-0000-000000000003");

    private static final UUID DISPATCHER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private static final Set<AssignmentStatus> ACTIVE_STATUSES = Set.of(
            AssignmentStatus.ASSIGNED,
            AssignmentStatus.ACCEPTED
    );

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private DispatchCourierAssignmentRepository courierAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    private DispatchServiceImpl dispatchService;

    private User activeCourier;
    private User dispatcherUser;

    @BeforeEach
    void setUp() {
        dispatchService = new DispatchServiceImpl(
                parcelRepository,
                courierAssignmentRepository,
                userRepository
        );

        activeCourier = User.builder()
                .id(COURIER_ID_1)
                .email("courier1@swiftdrop.lk")
                .fullName("Kamal Perera")
                .phone("+94771234567")
                .role(Role.ROLE_COURIER)
                .status(AccountStatus.ACTIVE)
                .build();

        dispatcherUser = User.builder()
                .id(DISPATCHER_ID)
                .email("dispatcher@swiftdrop.lk")
                .fullName("Nimal Jayawardena")
                .phone("+94779876543")
                .role(Role.ROLE_DISPATCHER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    // =========================================================================
    // 1. getUnassignedParcels() tests
    // =========================================================================

    @Test
    @DisplayName("getUnassignedParcels returns CREATED parcels without active assignments")
    void getUnassignedParcels_returnsCreatedParcelsWithoutActiveAssignments() {
        Parcel p1 = TestParcels.withId(PARCEL_ID_1);
        Parcel p2 = TestParcels.withId(PARCEL_ID_2);
        Parcel p3 = TestParcels.withId(PARCEL_ID_3);

        when(parcelRepository.findByStatusOrderByCreatedAtDesc(ParcelStatus.CREATED))
                .thenReturn(List.of(p1, p2, p3));

        CourierAssignment p2Assignment = CourierAssignment.builder()
                .id(UUID.randomUUID())
                .parcelId(PARCEL_ID_2)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(courierAssignmentRepository.findByStatusInOrderByAssignedAtDesc(ACTIVE_STATUSES))
                .thenReturn(List.of(p2Assignment));

        List<Parcel> unassigned = dispatchService.getUnassignedParcels();

        assertThat(unassigned).containsExactly(p1, p3);
        assertThat(unassigned).doesNotContain(p2);
    }

    @Test
    @DisplayName("getUnassignedParcels excludes parcels with ACCEPTED assignments")
    void getUnassignedParcels_excludesParcelsWithAcceptedAssignments() {
        Parcel p1 = TestParcels.withId(PARCEL_ID_1);

        when(parcelRepository.findByStatusOrderByCreatedAtDesc(ParcelStatus.CREATED))
                .thenReturn(List.of(p1));

        CourierAssignment acceptedAssignment = CourierAssignment.builder()
                .id(UUID.randomUUID())
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ACCEPTED)
                .build();

        when(courierAssignmentRepository.findByStatusInOrderByAssignedAtDesc(ACTIVE_STATUSES))
                .thenReturn(List.of(acceptedAssignment));

        List<Parcel> unassigned = dispatchService.getUnassignedParcels();

        assertThat(unassigned).isEmpty();
    }

    @Test
    @DisplayName("getUnassignedParcels preserves repository descending creation ordering")
    void getUnassignedParcels_preservesRepositoryOrdering() {
        Parcel p3 = TestParcels.withId(PARCEL_ID_3);
        Parcel p1 = TestParcels.withId(PARCEL_ID_1);

        when(parcelRepository.findByStatusOrderByCreatedAtDesc(ParcelStatus.CREATED))
                .thenReturn(List.of(p3, p1));

        when(courierAssignmentRepository.findByStatusInOrderByAssignedAtDesc(ACTIVE_STATUSES))
                .thenReturn(Collections.emptyList());

        List<Parcel> unassigned = dispatchService.getUnassignedParcels();

        assertThat(unassigned).containsExactly(p3, p1);
    }

    @Test
    @DisplayName("getUnassignedParcels includes parcels with completed or cancelled historical assignments")
    void getUnassignedParcels_includesParcelsWithCompletedOrCancelledAssignments() {
        Parcel p1 = TestParcels.withId(PARCEL_ID_1);

        when(parcelRepository.findByStatusOrderByCreatedAtDesc(ParcelStatus.CREATED))
                .thenReturn(List.of(p1));

        // When all assignments for P1 are non-active, active query returns empty
        when(courierAssignmentRepository.findByStatusInOrderByAssignedAtDesc(ACTIVE_STATUSES))
                .thenReturn(Collections.emptyList());

        List<Parcel> unassigned = dispatchService.getUnassignedParcels();

        assertThat(unassigned).containsExactly(p1);
    }

    // =========================================================================
    // 2. getActiveCouriers() tests
    // =========================================================================

    @Test
    @DisplayName("getActiveCouriers returns only users with ROLE_COURIER and AccountStatus.ACTIVE")
    void getActiveCouriers_returnsOnlyActiveUsersWithRoleCourier() {
        User pendingCourier = User.builder()
                .id(COURIER_ID_2)
                .email("pending@swiftdrop.lk")
                .role(Role.ROLE_COURIER)
                .status(AccountStatus.PENDING_APPROVAL)
                .build();

        User suspendedCourier = User.builder()
                .id(COURIER_ID_3)
                .email("suspended@swiftdrop.lk")
                .role(Role.ROLE_COURIER)
                .status(AccountStatus.SUSPENDED)
                .build();

        when(userRepository.findByRole(Role.ROLE_COURIER))
                .thenReturn(List.of(activeCourier, pendingCourier, suspendedCourier));

        List<User> activeCouriers = dispatchService.getActiveCouriers();

        assertThat(activeCouriers).containsExactly(activeCourier);
        assertThat(activeCouriers).doesNotContain(pendingCourier, suspendedCourier);
    }

    // =========================================================================
    // 3. assignCourier() tests
    // =========================================================================

    @Test
    @DisplayName("assignCourier successfully assigns a valid CREATED parcel to an active courier")
    void assignCourier_successfullyAssignsValidCreatedParcelToActiveCourier() {
        Parcel parcel = TestParcels.withId(PARCEL_ID_1);
        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .notes("Handle with care")
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(COURIER_ID_1)).thenReturn(Optional.of(activeCourier));
        when(userRepository.findById(DISPATCHER_ID)).thenReturn(Optional.of(dispatcherUser));
        when(courierAssignmentRepository.existsByParcelIdAndStatusIn(PARCEL_ID_1, ACTIVE_STATUSES)).thenReturn(false);

        when(courierAssignmentRepository.saveAndFlush(any(CourierAssignment.class)))
                .thenAnswer(invocation -> {
                    CourierAssignment a = invocation.getArgument(0);
                    return CourierAssignment.builder()
                            .id(ASSIGNMENT_ID)
                            .parcelId(a.getParcelId())
                            .courierId(a.getCourierId())
                            .dispatcherId(a.getDispatcherId())
                            .status(a.getStatus())
                            .notes(a.getNotes())
                            .assignedAt(Instant.now())
                            .updatedAt(Instant.now())
                            .version(0L)
                            .build();
                });

        AssignmentResponse response = dispatchService.assignCourier(request, DISPATCHER_ID);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(response.getParcelId()).isEqualTo(PARCEL_ID_1);
        assertThat(response.getCourierId()).isEqualTo(COURIER_ID_1);
        assertThat(response.getDispatcherId()).isEqualTo(DISPATCHER_ID);
        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(response.getNotes()).isEqualTo("Handle with care");
    }

    @Test
    @DisplayName("assignCourier rejects a parcel that does not exist")
    void assignCourier_rejectsParcelThatDoesNotExist() {
        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchService.assignCourier(request, DISPATCHER_ID))
                .isInstanceOf(ParcelNotFoundException.class)
                .hasMessageContaining(PARCEL_ID_1.toString());

        verify(courierAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("assignCourier rejects a parcel that is not in CREATED status")
    void assignCourier_rejectsParcelThatIsNotCreated() {
        Parcel parcel = TestParcels.withId(PARCEL_ID_1);
        parcel.changeStatus(ParcelStatus.PICKED_UP);

        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> dispatchService.assignCourier(request, DISPATCHER_ID))
                .isInstanceOf(InvalidAssignmentStateException.class)
                .hasMessageContaining("only CREATED parcels can be assigned");

        verify(courierAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("assignCourier rejects a non-courier user")
    void assignCourier_rejectsNonCourierUser() {
        Parcel parcel = TestParcels.withId(PARCEL_ID_1);
        User customer = User.builder()
                .id(COURIER_ID_1)
                .email("customer@swiftdrop.lk")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .build();

        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(COURIER_ID_1)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> dispatchService.assignCourier(request, DISPATCHER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a courier");

        verify(courierAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("assignCourier rejects an inactive courier")
    void assignCourier_rejectsInactiveCourier() {
        Parcel parcel = TestParcels.withId(PARCEL_ID_1);
        User suspendedCourier = User.builder()
                .id(COURIER_ID_1)
                .email("suspended@swiftdrop.lk")
                .role(Role.ROLE_COURIER)
                .status(AccountStatus.SUSPENDED)
                .build();

        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(COURIER_ID_1)).thenReturn(Optional.of(suspendedCourier));

        assertThatThrownBy(() -> dispatchService.assignCourier(request, DISPATCHER_ID))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("Courier account is not active");

        verify(courierAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("assignCourier rejects an invalid dispatcher without dispatcher or admin role")
    void assignCourier_rejectsInvalidDispatcher() {
        Parcel parcel = TestParcels.withId(PARCEL_ID_1);
        User invalidDispatcher = User.builder()
                .id(DISPATCHER_ID)
                .email("customer@swiftdrop.lk")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .build();

        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(COURIER_ID_1)).thenReturn(Optional.of(activeCourier));
        when(userRepository.findById(DISPATCHER_ID)).thenReturn(Optional.of(invalidDispatcher));

        assertThatThrownBy(() -> dispatchService.assignCourier(request, DISPATCHER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not authorized to dispatch");

        verify(courierAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("assignCourier rejects an already actively assigned parcel at the service level")
    void assignCourier_rejectsAlreadyActivelyAssignedParcel_serviceLevel() {
        Parcel parcel = TestParcels.withId(PARCEL_ID_1);
        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(COURIER_ID_1)).thenReturn(Optional.of(activeCourier));
        when(userRepository.findById(DISPATCHER_ID)).thenReturn(Optional.of(dispatcherUser));
        when(courierAssignmentRepository.existsByParcelIdAndStatusIn(PARCEL_ID_1, ACTIVE_STATUSES)).thenReturn(true);

        assertThatThrownBy(() -> dispatchService.assignCourier(request, DISPATCHER_ID))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining(PARCEL_ID_1.toString());

        verify(courierAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("assignCourier does not change the parcel status")
    void assignCourier_doesNotChangeParcelStatus() {
        Parcel parcel = TestParcels.withId(PARCEL_ID_1);
        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(COURIER_ID_1)).thenReturn(Optional.of(activeCourier));
        when(userRepository.findById(DISPATCHER_ID)).thenReturn(Optional.of(dispatcherUser));
        when(courierAssignmentRepository.existsByParcelIdAndStatusIn(PARCEL_ID_1, ACTIVE_STATUSES)).thenReturn(false);
        when(courierAssignmentRepository.saveAndFlush(any(CourierAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        dispatchService.assignCourier(request, DISPATCHER_ID);

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        verify(parcelRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignCourier handles active assignment unique constraint violation and throws DuplicateAssignmentException")
    void assignCourier_handlesActiveAssignmentUniqueConstraintViolation_throwsDuplicateAssignmentException() {
        Parcel parcel = TestParcels.withId(PARCEL_ID_1);
        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(COURIER_ID_1)).thenReturn(Optional.of(activeCourier));
        when(userRepository.findById(DISPATCHER_ID)).thenReturn(Optional.of(dispatcherUser));
        when(courierAssignmentRepository.existsByParcelIdAndStatusIn(PARCEL_ID_1, ACTIVE_STATUSES)).thenReturn(false);

        // Simulate concurrent insert unique violation matching idx_active_courier_assignment
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key value violates unique constraint \"idx_active_courier_assignment\"",
                new SQLException("duplicate key", "23505"),
                "idx_active_courier_assignment"
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "could not execute statement; SQL [n/a]; constraint [idx_active_courier_assignment]",
                cve
        );

        when(courierAssignmentRepository.saveAndFlush(any(CourierAssignment.class))).thenThrow(dive);

        assertThatThrownBy(() -> dispatchService.assignCourier(request, DISPATCHER_ID))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining(PARCEL_ID_1.toString());
    }

    @Test
    @DisplayName("assignCourier rethrows unrelated DataIntegrityViolationException errors unchanged")
    void assignCourier_rethrowsUnrelatedDataIntegrityViolationException() {
        Parcel parcel = TestParcels.withId(PARCEL_ID_1);
        AssignCourierRequest request = AssignCourierRequest.builder()
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .build();

        when(parcelRepository.findById(PARCEL_ID_1)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(COURIER_ID_1)).thenReturn(Optional.of(activeCourier));
        when(userRepository.findById(DISPATCHER_ID)).thenReturn(Optional.of(dispatcherUser));
        when(courierAssignmentRepository.existsByParcelIdAndStatusIn(PARCEL_ID_1, ACTIVE_STATUSES)).thenReturn(false);

        // Simulate unrelated foreign key integrity violation
        DataIntegrityViolationException unrelatedDive = new DataIntegrityViolationException(
                "violates foreign key constraint fk_unrelated_table"
        );

        when(courierAssignmentRepository.saveAndFlush(any(CourierAssignment.class))).thenThrow(unrelatedDive);

        assertThatThrownBy(() -> dispatchService.assignCourier(request, DISPATCHER_ID))
                .isSameAs(unrelatedDive);
    }

    // =========================================================================
    // 4. getCourierWorkloads() tests
    // =========================================================================

    @Test
    @DisplayName("getCourierWorkloads includes active couriers with zero assignments and counts only active ones")
    void getCourierWorkloads_includesActiveCouriersWithZeroAssignments() {
        User courierWithZero = User.builder()
                .id(COURIER_ID_2)
                .email("courier2@swiftdrop.lk")
                .fullName("Saman Kumara")
                .phone("+94772222222")
                .role(Role.ROLE_COURIER)
                .status(AccountStatus.ACTIVE)
                .build();

        when(userRepository.findByRole(Role.ROLE_COURIER))
                .thenReturn(List.of(activeCourier, courierWithZero));

        // Grouped count projection returns [UUID courierId, Long count]
        List<Object[]> projection = new ArrayList<>();
        projection.add(new Object[]{COURIER_ID_1, 2L});

        when(courierAssignmentRepository.countActiveAssignmentsGroupedByCourier(ACTIVE_STATUSES))
                .thenReturn(projection);

        List<CourierWorkloadResponse> workloads = dispatchService.getCourierWorkloads();

        assertThat(workloads).hasSize(2);

        CourierWorkloadResponse w1 = workloads.stream()
                .filter(w -> w.getCourierId().equals(COURIER_ID_1))
                .findFirst()
                .orElseThrow();
        assertThat(w1.getActiveAssignmentsCount()).isEqualTo(2L);
        assertThat(w1.getWorkloadStatus()).isEqualTo("MODERATE");

        CourierWorkloadResponse w2 = workloads.stream()
                .filter(w -> w.getCourierId().equals(COURIER_ID_2))
                .findFirst()
                .orElseThrow();
        assertThat(w2.getActiveAssignmentsCount()).isZero();
        assertThat(w2.getWorkloadStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("getCourierWorkloads calculates AVAILABLE, MODERATE, and BUSY thresholds correctly")
    void getCourierWorkloads_calculatesAvailableModerateAndBusyCorrectly() {
        User courierAvailable = User.builder()
                .id(COURIER_ID_1)
                .role(Role.ROLE_COURIER)
                .status(AccountStatus.ACTIVE)
                .build();

        User courierModerate = User.builder()
                .id(COURIER_ID_2)
                .role(Role.ROLE_COURIER)
                .status(AccountStatus.ACTIVE)
                .build();

        User courierBusy = User.builder()
                .id(COURIER_ID_3)
                .role(Role.ROLE_COURIER)
                .status(AccountStatus.ACTIVE)
                .build();

        when(userRepository.findByRole(Role.ROLE_COURIER))
                .thenReturn(List.of(courierAvailable, courierModerate, courierBusy));

        List<Object[]> projection = List.of(
                new Object[]{COURIER_ID_2, 3L}, // 1-3 is MODERATE
                new Object[]{COURIER_ID_3, 4L}  // > 3 is BUSY
        );

        when(courierAssignmentRepository.countActiveAssignmentsGroupedByCourier(ACTIVE_STATUSES))
                .thenReturn(projection);

        List<CourierWorkloadResponse> workloads = dispatchService.getCourierWorkloads();

        Map<UUID, String> statusMap = new HashMap<>();
        workloads.forEach(w -> statusMap.put(w.getCourierId(), w.getWorkloadStatus()));

        assertThat(statusMap.get(COURIER_ID_1)).isEqualTo("AVAILABLE");
        assertThat(statusMap.get(COURIER_ID_2)).isEqualTo("MODERATE");
        assertThat(statusMap.get(COURIER_ID_3)).isEqualTo("BUSY");
    }

    // =========================================================================
    // 5. cancelAssignment() tests
    // =========================================================================

    @Test
    @DisplayName("cancelAssignment successfully cancels an ASSIGNED assignment")
    void cancelAssignment_successfullyCancelsAssignedAssignment() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(courierAssignmentRepository.save(any(CourierAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AssignmentResponse response = dispatchService.cancelAssignment(ASSIGNMENT_ID, "Courier unavailable");

        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(response.getNotes()).contains("Cancel reason: Courier unavailable");
        verify(courierAssignmentRepository).save(assignment);
    }

    @Test
    @DisplayName("cancelAssignment successfully cancels an ACCEPTED assignment")
    void cancelAssignment_successfullyCancelsAcceptedAssignment() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ACCEPTED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(courierAssignmentRepository.save(any(CourierAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AssignmentResponse response = dispatchService.cancelAssignment(ASSIGNMENT_ID, "Delivery address updated");

        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(response.getNotes()).contains("Cancel reason: Delivery address updated");
        verify(courierAssignmentRepository).save(assignment);
    }

    @Test
    @DisplayName("cancelAssignment rejects missing assignment")
    void cancelAssignment_rejectsMissingAssignment() {
        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchService.cancelAssignment(ASSIGNMENT_ID, "Any reason"))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining(ASSIGNMENT_ID.toString());

        verify(courierAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelAssignment rejects already COMPLETED assignment")
    void cancelAssignment_rejectsCompletedAssignment() {
        CourierAssignment completedAssignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.COMPLETED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(completedAssignment));

        assertThatThrownBy(() -> dispatchService.cancelAssignment(ASSIGNMENT_ID, "Too late"))
                .isInstanceOf(InvalidAssignmentStateException.class)
                .hasMessageContaining("cancel");

        verify(courierAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelAssignment rejects already CANCELLED assignment")
    void cancelAssignment_rejectsAlreadyCancelledAssignment() {
        CourierAssignment cancelledAssignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.CANCELLED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(cancelledAssignment));

        assertThatThrownBy(() -> dispatchService.cancelAssignment(ASSIGNMENT_ID, "Duplicate cancel"))
                .isInstanceOf(InvalidAssignmentStateException.class)
                .hasMessageContaining("cancel");

        verify(courierAssignmentRepository, never()).save(any());
    }

    // =========================================================================
    // getAssignmentsForCourier tests (1 scenario)
    // =========================================================================

    @Test
    @DisplayName("getAssignmentsForCourier returns assignments belonging to courier in descending order")
    void getAssignmentsForCourier_returnsAssignmentsBelongingToCourierInDescendingOrder() {
        CourierAssignment a1 = CourierAssignment.builder()
                .id(UUID.randomUUID())
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ACCEPTED)
                .assignedAt(Instant.now().minusSeconds(100))
                .build();

        CourierAssignment a2 = CourierAssignment.builder()
                .id(UUID.randomUUID())
                .parcelId(PARCEL_ID_2)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .assignedAt(Instant.now().minusSeconds(500))
                .build();

        when(courierAssignmentRepository.findByCourierIdOrderByAssignedAtDesc(COURIER_ID_1))
                .thenReturn(List.of(a1, a2));

        List<AssignmentResponse> results = dispatchService.getAssignmentsForCourier(COURIER_ID_1);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(a1.getId());
        assertThat(results.get(1).getId()).isEqualTo(a2.getId());
        verify(courierAssignmentRepository).findByCourierIdOrderByAssignedAtDesc(COURIER_ID_1);
    }

    // =========================================================================
    // acceptAssignment tests (5 scenarios)
    // =========================================================================

    @Test
    @DisplayName("acceptAssignment successfully accepts ASSIGNED assignment")
    void acceptAssignment_successfullyAcceptsAssignedAssignment() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .assignedAt(Instant.now())
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(courierAssignmentRepository.save(any(CourierAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignmentResponse response = dispatchService.acceptAssignment(ASSIGNMENT_ID, COURIER_ID_1);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.ACCEPTED);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ACCEPTED);
        verify(courierAssignmentRepository).save(assignment);
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("acceptAssignment rejects non-existent assignment")
    void acceptAssignment_rejectsMissingAssignment() {
        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchService.acceptAssignment(ASSIGNMENT_ID, COURIER_ID_1))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining(ASSIGNMENT_ID.toString());

        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("acceptAssignment rejects unauthorized courier")
    void acceptAssignment_rejectsUnauthorizedCourier() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        // COURIER_ID_2 attempts to accept COURIER_ID_1's assignment
        assertThatThrownBy(() -> dispatchService.acceptAssignment(ASSIGNMENT_ID, COURIER_ID_2))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("acceptAssignment rejects non-ASSIGNED status")
    void acceptAssignment_rejectsNonAssignedStatus() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ACCEPTED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> dispatchService.acceptAssignment(ASSIGNMENT_ID, COURIER_ID_1))
                .isInstanceOf(InvalidAssignmentStateException.class)
                .hasMessageContaining("accept");

        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("acceptAssignment does not modify parcel or parcel status")
    void acceptAssignment_doesNotModifyParcelStatus() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(courierAssignmentRepository.save(any(CourierAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        dispatchService.acceptAssignment(ASSIGNMENT_ID, COURIER_ID_1);

        verifyNoInteractions(parcelRepository);
    }

    // =========================================================================
    // rejectAssignment tests (5 scenarios)
    // =========================================================================

    @Test
    @DisplayName("rejectAssignment successfully rejects ASSIGNED assignment with reason")
    void rejectAssignment_successfullyRejectsAssignedAssignmentWithReason() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .assignedAt(Instant.now())
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(courierAssignmentRepository.save(any(CourierAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignmentResponse response = dispatchService.rejectAssignment(ASSIGNMENT_ID, COURIER_ID_1, "Vehicle breakdown");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.REJECTED);
        assertThat(response.getNotes()).contains("Vehicle breakdown");
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.REJECTED);
        assertThat(assignment.getNotes()).contains("Vehicle breakdown");
        verify(courierAssignmentRepository).save(assignment);
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("rejectAssignment rejects non-existent assignment")
    void rejectAssignment_rejectsMissingAssignment() {
        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchService.rejectAssignment(ASSIGNMENT_ID, COURIER_ID_1, "Vehicle breakdown"))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining(ASSIGNMENT_ID.toString());

        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("rejectAssignment rejects unauthorized courier")
    void rejectAssignment_rejectsUnauthorizedCourier() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> dispatchService.rejectAssignment(ASSIGNMENT_ID, COURIER_ID_2, "Cannot take it"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("rejectAssignment rejects non-ASSIGNED status")
    void rejectAssignment_rejectsNonAssignedStatus() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ACCEPTED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> dispatchService.rejectAssignment(ASSIGNMENT_ID, COURIER_ID_1, "Cannot take it"))
                .isInstanceOf(InvalidAssignmentStateException.class)
                .hasMessageContaining("reject");

        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("rejectAssignment does not modify parcel or parcel status")
    void rejectAssignment_doesNotModifyParcelStatus() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(courierAssignmentRepository.save(any(CourierAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        dispatchService.rejectAssignment(ASSIGNMENT_ID, COURIER_ID_1, "Reason");

        verifyNoInteractions(parcelRepository);
    }

    // =========================================================================
    // completeAssignment tests (6 scenarios)
    // =========================================================================

    @Test
    @DisplayName("completeAssignment successfully completes ACCEPTED assignment")
    void completeAssignment_successfullyCompletesAcceptedAssignment() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ACCEPTED)
                .assignedAt(Instant.now().minusSeconds(3600))
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(courierAssignmentRepository.save(any(CourierAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignmentResponse response = dispatchService.completeAssignment(ASSIGNMENT_ID, COURIER_ID_1);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        verify(courierAssignmentRepository).save(assignment);
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("completeAssignment rejects non-existent assignment")
    void completeAssignment_rejectsMissingAssignment() {
        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchService.completeAssignment(ASSIGNMENT_ID, COURIER_ID_1))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining(ASSIGNMENT_ID.toString());

        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("completeAssignment rejects unauthorized courier")
    void completeAssignment_rejectsUnauthorizedCourier() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ACCEPTED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> dispatchService.completeAssignment(ASSIGNMENT_ID, COURIER_ID_2))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ACCEPTED);
        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("completeAssignment rejects non-ACCEPTED status (e.g. ASSIGNED)")
    void completeAssignment_rejectsNonAcceptedStatus() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> dispatchService.completeAssignment(ASSIGNMENT_ID, COURIER_ID_1))
                .isInstanceOf(InvalidAssignmentStateException.class)
                .hasMessageContaining("complete");

        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("completeAssignment rejects already CANCELLED or COMPLETED assignment")
    void completeAssignment_rejectsAlreadyCancelledOrCompletedAssignment() {
        CourierAssignment cancelledAssignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.CANCELLED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(cancelledAssignment));

        assertThatThrownBy(() -> dispatchService.completeAssignment(ASSIGNMENT_ID, COURIER_ID_1))
                .isInstanceOf(InvalidAssignmentStateException.class)
                .hasMessageContaining("complete");

        verify(courierAssignmentRepository, never()).save(any());
        verifyNoInteractions(parcelRepository);
    }

    @Test
    @DisplayName("completeAssignment does not modify parcel or parcel status")
    void completeAssignment_doesNotModifyParcelStatus() {
        CourierAssignment assignment = CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID_1)
                .courierId(COURIER_ID_1)
                .dispatcherId(DISPATCHER_ID)
                .status(AssignmentStatus.ACCEPTED)
                .build();

        when(courierAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(courierAssignmentRepository.save(any(CourierAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        dispatchService.completeAssignment(ASSIGNMENT_ID, COURIER_ID_1);

        verifyNoInteractions(parcelRepository);
    }
}
