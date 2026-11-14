package com.swiftdrop.backend.modules.dispatch.controller;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.exception.AccountNotActiveException;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.dispatch.dto.AssignmentResponse;
import com.swiftdrop.backend.modules.dispatch.dto.RejectAssignmentRequest;
import com.swiftdrop.backend.modules.dispatch.service.DispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courier/assignments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COURIER')")
public class CourierAssignmentController {

    private final DispatchService dispatchService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<AssignmentResponse>> getMyAssignments(Principal principal) {
        UUID courierId = resolveCourierId(principal);
        List<AssignmentResponse> assignments = dispatchService.getAssignmentsForCourier(courierId);
        return ResponseEntity.ok(assignments);
    }

    @PostMapping("/{assignmentId}/accept")
    public ResponseEntity<AssignmentResponse> acceptAssignment(
            @PathVariable UUID assignmentId,
            Principal principal) {
        UUID courierId = resolveCourierId(principal);
        AssignmentResponse response = dispatchService.acceptAssignment(assignmentId, courierId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{assignmentId}/reject")
    public ResponseEntity<AssignmentResponse> rejectAssignment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody(required = false) RejectAssignmentRequest request,
            Principal principal) {
        UUID courierId = resolveCourierId(principal);
        String reason = (request != null) ? request.getReason() : null;
        AssignmentResponse response = dispatchService.rejectAssignment(assignmentId, courierId, reason);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{assignmentId}/complete")
    public ResponseEntity<AssignmentResponse> completeAssignment(
            @PathVariable UUID assignmentId,
            Principal principal) {
        UUID courierId = resolveCourierId(principal);
        AssignmentResponse response = dispatchService.completeAssignment(assignmentId, courierId);
        return ResponseEntity.ok(response);
    }

    private UUID resolveCourierId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("No authenticated courier principal found");
        }
        User user = userRepository.findByEmail(principal.getName().trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Courier account not found for email: " + principal.getName()));

        if (user.getRole() != Role.ROLE_COURIER) {
            throw new AccessDeniedException("User is not authorized as a courier: " + user.getId());
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Courier account is not active: " + user.getId());
        }

        return user.getId();
    }
}
