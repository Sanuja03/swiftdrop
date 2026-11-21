
package com.swiftdrop.backend.modules.notifications.controller;

import com.swiftdrop.backend.modules.notifications.dto.NotificationResponse;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

    public NotificationController(
            NotificationService notificationService,
            JdbcTemplate jdbcTemplate
    ) {
        this.notificationService = notificationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private UUID getAuthenticatedUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        String email = authentication.getName();

        List<UUID> userIds = jdbcTemplate.query(
                "SELECT id FROM public.users WHERE email = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                email
        );

        if (userIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user not found"
            );
        }

        return userIds.get(0);
    }

    @GetMapping
    public List<NotificationResponse> getMyNotifications() {
        return notificationService.getMyNotifications(
                getAuthenticatedUserId()
        );
    }

    @GetMapping("/unread")
    public List<NotificationResponse> getUnreadNotifications() {
        return notificationService.getUnreadNotifications(
                getAuthenticatedUserId()
        );
    }

    @GetMapping("/unread-count")
    public long getUnreadCount() {
        return notificationService.getUnreadCount(
                getAuthenticatedUserId()
        );
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markAsRead(
            @PathVariable UUID notificationId
    ) {
        return notificationService.markAsRead(
                notificationId,
                getAuthenticatedUserId()
        );
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleInvalidId(MethodArgumentTypeMismatchException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Notification ID must be a valid UUID.");
    }

}
