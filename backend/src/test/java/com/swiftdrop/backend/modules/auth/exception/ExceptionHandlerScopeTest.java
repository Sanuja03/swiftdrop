package com.swiftdrop.backend.modules.auth.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.common.security.NotSignedInExceptionHandler;
import com.swiftdrop.backend.modules.auth.controller.AuthController;
import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.auth.service.AuthService;
import com.swiftdrop.backend.modules.booking.controller.BookingExceptionHandler;
import com.swiftdrop.backend.modules.pricing.controller.PricingExceptionHandler;
import com.swiftdrop.backend.modules.tracking.controller.TrackingExceptionHandler;

/**
 * The booking, pricing and tracking problem handlers run before the auth module's handler,
 * so they must not take over auth's own errors: auth keeps its validation error format.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, PricingExceptionHandler.class, BookingExceptionHandler.class,
        TrackingExceptionHandler.class, NotSignedInExceptionHandler.class})
class ExceptionHandlerScopeTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuthService authService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void authValidationErrorsKeepTheAuthFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }
}
