package com.swiftdrop.backend.modules.receipt.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.common.security.CurrentCustomer;
import com.swiftdrop.backend.common.security.CurrentCustomerProvider;
import com.swiftdrop.backend.common.security.DefaultSecurityConfig;
import com.swiftdrop.backend.common.security.NotSignedInException;
import com.swiftdrop.backend.common.security.NotSignedInExceptionHandler;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.receipt.dto.ReceiptFile;
import com.swiftdrop.backend.modules.receipt.service.ReceiptService;
import com.swiftdrop.backend.modules.tracking.controller.TrackingExceptionHandler;

@WebMvcTest(ReceiptController.class)
@Import({DefaultSecurityConfig.class, TrackingExceptionHandler.class, NotSignedInExceptionHandler.class})
class ReceiptControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");

    /** The auth module's JWT filter is part of every web test slice and needs this bean. */
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptService receiptService;

    @MockitoBean
    private CurrentCustomerProvider currentCustomerProvider;

    private void signedInAsCustomer() {
        when(currentCustomerProvider.currentCustomer()).thenReturn(new CurrentCustomer(CUSTOMER_ID, "Test Customer"));
    }

    @Test
    @WithMockUser
    void downloadsReceiptAsPdfAttachment() throws Exception {
        signedInAsCustomer();
        byte[] pdf = {'%', 'P', 'D', 'F'};
        when(receiptService.getReceipt("SDP7845621", CUSTOMER_ID)).thenReturn(new ReceiptFile("RCT-SDP7845621.pdf", pdf));

        mockMvc.perform(get("/api/v1/receipts/SDP7845621"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"RCT-SDP7845621.pdf\""))
                .andExpect(content().bytes(pdf));
    }

    @Test
    @WithMockUser
    void unknownOrSomeoneElsesParcelReturns404() throws Exception {
        signedInAsCustomer();
        when(receiptService.getReceipt(anyString(), eq(CUSTOMER_ID))).thenThrow(new ParcelNotFoundException("SDP0000000"));

        mockMvc.perform(get("/api/v1/receipts/SDP0000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Parcel not found"));
    }

    @Test
    @WithMockUser
    void userWithoutACustomerAccountIsUnauthorized() throws Exception {
        when(currentCustomerProvider.currentCustomer()).thenThrow(new NotSignedInException());

        mockMvc.perform(get("/api/v1/receipts/SDP7845621"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(receiptService);
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/receipts/SDP7845621"))
                .andExpect(status().isUnauthorized());
    }
}
