package com.swiftdrop.backend.modules.notifications.controller;

import com.swiftdrop.backend.modules.auth.security.*;
import com.swiftdrop.backend.modules.notifications.dto.NotificationResponse;
import com.swiftdrop.backend.modules.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, CustomAccessDeniedHandler.class, CustomAuthenticationEntryPoint.class, JwtAuthenticationFilter.class})
class NotificationControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private NotificationService service;
    @MockitoBean private JdbcTemplate jdbc;
    @MockitoBean private JwtTokenProvider tokens;
    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        doReturn(List.of(userId)).when(jdbc).query(anyString(), any(RowMapper.class), eq("owner@example.test"));
    }

    @Test
    void anonymousUsersCannotReadNotifications() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "owner@example.test", roles = "CUSTOMER")
    void listUnreadAndCountUseTheAuthenticatedOwner() throws Exception {
        var notification = new NotificationResponse(notificationId, null, "Title", "Message", "GENERAL", false, Instant.now(), null);
        when(service.getMyNotifications(userId)).thenReturn(List.of(notification));
        when(service.getUnreadNotifications(userId)).thenReturn(List.of(notification));
        when(service.getUnreadCount(userId)).thenReturn(1L);
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(notificationId.toString()));
        mvc.perform(get("/api/v1/notifications/unread")).andExpect(status().isOk()).andExpect(jsonPath("$[0].isRead").value(false));
        mvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isOk()).andExpect(content().string("1"));
        verify(service).getMyNotifications(userId);
        verify(service).getUnreadNotifications(userId);
    }

    @Test
    @WithMockUser(username = "owner@example.test", roles = "CUSTOMER")
    void spoofedOwnerParameterCannotChangeWhoMarksTheNotificationRead() throws Exception {
        mvc.perform(patch("/api/v1/notifications/{id}/read", notificationId).param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        verify(service).markAsRead(notificationId, userId);
    }

    @Test
    @WithMockUser(username = "owner@example.test", roles = "CUSTOMER")
    void ownershipFailureKeepsItsNotFoundStatus() throws Exception {
        when(service.markAsRead(notificationId, userId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        mvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "owner@example.test", roles = "CUSTOMER")
    void missingDatabaseUserReturnsUnauthorized() throws Exception {
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), eq("owner@example.test"));
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "owner@example.test", roles = "CUSTOMER")
    void malformedIdReturnsBadRequest() throws Exception {
        mvc.perform(patch("/api/v1/notifications/not-a-uuid/read")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
