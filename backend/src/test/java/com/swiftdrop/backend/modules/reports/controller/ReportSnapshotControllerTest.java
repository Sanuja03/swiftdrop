package com.swiftdrop.backend.modules.reports.controller;

import com.swiftdrop.backend.modules.auth.security.*;
import com.swiftdrop.backend.modules.reports.dto.*;
import com.swiftdrop.backend.modules.reports.service.ReportSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportSnapshotController.class)
@Import({SecurityConfig.class, CustomAccessDeniedHandler.class, CustomAuthenticationEntryPoint.class, JwtAuthenticationFilter.class})
class ReportSnapshotControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ReportSnapshotService service;
    @MockitoBean private JwtTokenProvider tokens;
    private final UUID id = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN"})
    void adminCanSaveListAndOpen(String role) throws Exception {
        var start = LocalDate.of(2026, 11, 1);
        var end = LocalDate.of(2026, 11, 7);
        var snapshot = new ReportSnapshotResponse(id, UUID.randomUUID(), Instant.now(), start, end,
                new ReportSnapshotData(null, null, List.of()));
        when(service.save(any(), eq("owner@example.test"))).thenReturn(snapshot);
        when(service.get(id)).thenReturn(snapshot);
        when(service.list(0, 20)).thenReturn(new ReportSnapshotPage(List.of(), 0, 0, 20));
        mvc.perform(post("/api/v1/admin/reports/snapshots").with(user("owner@example.test").roles(role))
                .contentType("application/json").content(body()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        mvc.perform(get("/api/v1/admin/reports/snapshots").with(user("owner@example.test").roles(role)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/reports/snapshots/{id}", id).with(user("owner@example.test").roles(role)))
                .andExpect(status().isOk());
        verify(service).save(new SaveReportSnapshotRequest(id, start, end), "owner@example.test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER"})
    void otherRolesCannotSaveOrRead(String role) throws Exception {
        mvc.perform(get("/api/v1/admin/reports/snapshots").with(user("test").roles(role)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/reports/snapshots").with(user("test").roles(role))
                .contentType("application/json").content(body())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousUsersCannotSaveOrRead() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/snapshots")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/reports/snapshots").contentType("application/json").content(body()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedAndIncompleteRequestsReturn400() throws Exception {
        mvc.perform(post("/api/v1/admin/reports/snapshots").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reports/snapshots/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reports/snapshots").param("page", "abc")).andExpect(status().isBadRequest());
    }

    private String body() {
        return """
                {"id":"%s","startDate":"2026-11-01","endDate":"2026-11-07"}
                """.formatted(id);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void savedDateRangeIsPassedToListingService() throws Exception {
        var start = LocalDate.of(2026, 11, 12);
        var end = LocalDate.of(2026, 11, 18);
        mvc.perform(get("/api/v1/admin/reports/snapshots").param("page", "1")
                .param("startDate", start.toString()).param("endDate", end.toString()))
                .andExpect(status().isOk());
        verify(service).list(1, 20, start, end);
        mvc.perform(get("/api/v1/admin/reports/snapshots").param("startDate", "bad-date"))
                .andExpect(status().isBadRequest());
    }
}
