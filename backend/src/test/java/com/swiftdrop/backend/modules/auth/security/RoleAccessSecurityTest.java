package com.swiftdrop.backend.modules.auth.security;

import com.swiftdrop.backend.modules.auth.controller.UserController;
import com.swiftdrop.backend.modules.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;
import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.dto.UserProfileDto;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        CustomAccessDeniedHandler.class,
        CustomAuthenticationEntryPoint.class,
        JwtAuthenticationFilter.class
})
class RoleAccessSecurityTest {

    private static final UUID TEST_USER_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void dispatcherCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isForbidden());
        verify(authService, never()).getAllUsers();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/courier/deliveries/today", "/api/v1/admin/reports/total-parcels", "/api/v1/admin/users"})
    @WithMockUser(roles = "CUSTOMER")
    void customersCannotAccessOperationalBusinessRoutes(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @Test
    void courierBusinessRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/courier/deliveries/today")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN"})
    void administratorsCanManageUserStatus(String role) throws Exception {
        when(authService.updateUserStatus(TEST_USER_ID, AccountStatus.SUSPENDED))
                .thenReturn(UserProfileDto.builder().id(TEST_USER_ID).status(AccountStatus.SUSPENDED).build());
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", TEST_USER_ID)
                .param("status", "SUSPENDED").with(user("test").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        verify(authService).updateUserStatus(TEST_USER_ID, AccountStatus.SUSPENDED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "COURIER", "MERCHANT", "DISPATCHER"})
    void otherRolesCannotManageUserStatus(String role) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", TEST_USER_ID)
                .param("status", "SUSPENDED").with(user("test").roles(role)))
                .andExpect(status().isForbidden());
        verify(authService, never()).updateUserStatus(any(), any());
    }

    @Test
    void changingUserStatusRequiresSignIn() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", TEST_USER_ID)
                .param("status", "SUSPENDED"))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).updateUserStatus(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidMerchantStatusOverrideReturnsItsIntendedErrorInsteadOf500() throws Exception {
        when(authService.updateUserStatus(TEST_USER_ID, AccountStatus.REJECTED)).thenThrow(
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Use merchant review to change application status"));
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", TEST_USER_ID)
                .param("status", "REJECTED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Use merchant review to change application status"));
    }

    @Test
    @DisplayName("Unauthenticated request to admin endpoint should return 401 Unauthorized")
    void unauthenticatedRequestToAdminReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Customer attempting to access Admin endpoint should receive 403 Forbidden")
    void customerAccessingAdminReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access Denied: You do not have permission to access this resource"));
    }

    @Test
    @WithMockUser(roles = "COURIER")
    @DisplayName("Courier attempting to access Admin endpoint should receive 403 Forbidden")
    void courierAccessingAdminReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin accessing Admin endpoint should succeed with 200 OK")
    void adminAccessingAdminSucceeds() throws Exception {
        when(authService.getAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }
}
