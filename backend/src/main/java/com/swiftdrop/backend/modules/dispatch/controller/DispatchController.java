package com.swiftdrop.backend.modules.dispatch.controller;

import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.dispatch.dto.ActiveCourierResponse;
import com.swiftdrop.backend.modules.dispatch.dto.AssignCourierRequest;
import com.swiftdrop.backend.modules.dispatch.dto.AssignmentResponse;
import com.swiftdrop.backend.modules.dispatch.dto.CancelAssignmentRequest;
import com.swiftdrop.backend.modules.dispatch.dto.CourierWorkloadResponse;
import com.swiftdrop.backend.modules.dispatch.dto.UnassignedParcelResponse;
import com.swiftdrop.backend.modules.dispatch.service.DispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatcher")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
public class DispatchController {

    private final DispatchService dispatchService;
    private final UserRepository userRepository;

    @GetMapping("/parcels/unassigned")
    public ResponseEntity<List<UnassignedParcelResponse>> getUnassignedParcels() {
        List<UnassignedParcelResponse> unassigned = dispatchService.getUnassignedParcels().stream()
                .map(UnassignedParcelResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(unassigned);
    }

    @GetMapping("/couriers/active")
    public ResponseEntity<List<ActiveCourierResponse>> getActiveCouriers() {
        List<ActiveCourierResponse> couriers = dispatchService.getActiveCouriers().stream()
                .map(ActiveCourierResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(couriers);
    }

    @PostMapping("/assignments")
    public ResponseEntity<AssignmentResponse> assignCourier(
            @Valid @RequestBody AssignCourierRequest request,
            Principal principal) {
        UUID dispatcherId = resolveDispatcherId(principal);
        AssignmentResponse response = dispatchService.assignCourier(request, dispatcherId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/workloads")
    public ResponseEntity<List<CourierWorkloadResponse>> getCourierWorkloads() {
        return ResponseEntity.ok(dispatchService.getCourierWorkloads());
    }

    @PatchMapping("/assignments/{assignmentId}/cancel")
    public ResponseEntity<AssignmentResponse> cancelAssignment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody(required = false) CancelAssignmentRequest request) {
        String reason = (request != null) ? request.getReason() : null;
        AssignmentResponse response = dispatchService.cancelAssignment(assignmentId, reason);
        return ResponseEntity.ok(response);
    }

    private UUID resolveDispatcherId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("No authenticated dispatcher principal found");
        }
        return userRepository.findByEmail(principal.getName().trim().toLowerCase())
                .map(User::getId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Dispatcher account not found for email: " + principal.getName()));
    }
}
