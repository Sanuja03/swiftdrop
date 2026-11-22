package com.swiftdrop.backend.modules.reports.controller;

import com.swiftdrop.backend.modules.auth.security.*;
import com.swiftdrop.backend.modules.reports.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, CustomAccessDeniedHandler.class,
        CustomAuthenticationEntryPoint.class, JwtAuthenticationFilter.class})
class ReportControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ReportService reports;
    @MockitoBean private JwtTokenProvider tokens;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/summary")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "COURIER", "MERCHANT", "DISPATCHER"})
    void requiresAdmin(String role) throws Exception {
        mvc.perform(get("/api/v1/admin/reports/summary").with(user("test").roles(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN"})
    void adminCanReadEveryReport(String role) throws Exception {
        mvc.perform(get("/api/v1/admin/reports/summary").with(user("test").roles(role)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/reports/success-rate").with(user("test").roles(role)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/reports/daily-deliveries").with(user("test").roles(role)))
                .andExpect(status().isOk());
        verify(reports).getReportSummary();
        verify(reports).getDeliverySuccessRate();
        verify(reports).getDailyDeliveries(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void acceptsExplicitPeriod() throws Exception {
        var start = LocalDate.of(2026, 11, 1);
        var end = LocalDate.of(2026, 11, 7);
        when(reports.getDailyDeliveries(start, end)).thenReturn(List.of());
        mvc.perform(get("/api/v1/admin/reports/daily-deliveries")
                .param("startDate", start.toString()).param("endDate", end.toString()))
                .andExpect(status().isOk());
        verify(reports).getDailyDeliveries(start, end);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void defaultsToSevenSriLankanCalendarDays() throws Exception {
        var today = LocalDate.now(ZoneId.of("Asia/Colombo"));
        mvc.perform(get("/api/v1/admin/reports/daily-deliveries")).andExpect(status().isOk());
        verify(reports).getDailyDeliveries(today.minusDays(6), today);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsReversedDates() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/daily-deliveries")
                .param("startDate", "2026-11-08").param("endDate", "2026-11-01"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsMalformedDates() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/daily-deliveries").param("startDate", "bad-date"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void successRateAcceptsItsOwnRangeAndRejectsIncompleteRange() throws Exception {
        var start = LocalDate.of(2026, 11, 1);
        var end = LocalDate.of(2026, 11, 7);
        mvc.perform(get("/api/v1/admin/reports/success-rate")
                .param("startDate", start.toString()).param("endDate", end.toString()))
                .andExpect(status().isOk());
        verify(reports).getDeliverySuccessRate(start, end);
        mvc.perform(get("/api/v1/admin/reports/success-rate").param("startDate", start.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reports/success-rate")
                .param("startDate", end.toString()).param("endDate", start.toString()))
                .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(reports);
    }
}
