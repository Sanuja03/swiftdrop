package com.swiftdrop.backend.modules.auth.security;

import com.swiftdrop.backend.modules.auth.controller.UserController;
import com.swiftdrop.backend.modules.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

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

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Customer attempting to access Courier endpoint should receive 403 Forbidden")
    void customerAccessingCourierReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/courier/test"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(roles = "COURIER")
    @DisplayName("Courier accessing Courier endpoint should succeed with 200 OK")
    void courierAccessingCourierSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/courier/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Authorized access to Courier portal"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Customer accessing Customer endpoint should succeed with 200 OK")
    void customerAccessingCustomerSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/customer/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Authorized access to Customer portal"));
    }
}
