package com.swiftdrop.backend.modules.receipt.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.common.security.DefaultSecurityConfig;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.receipt.dto.ReceiptFile;
import com.swiftdrop.backend.modules.receipt.service.ReceiptService;
import com.swiftdrop.backend.modules.tracking.controller.TrackingExceptionHandler;

@WebMvcTest(ReceiptController.class)
@Import({DefaultSecurityConfig.class, TrackingExceptionHandler.class})
class ReceiptControllerTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.swiftdrop.backend.modules.auth.security.JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptService receiptService;

    @Test
    @WithMockUser
    void downloadsReceiptAsPdfAttachment() throws Exception {
        byte[] pdf = {'%', 'P', 'D', 'F'};
        when(receiptService.getReceipt("SDP7845621")).thenReturn(new ReceiptFile("RCT-SDP7845621.pdf", pdf));

        mockMvc.perform(get("/api/v1/receipts/SDP7845621"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"RCT-SDP7845621.pdf\""))
                .andExpect(content().bytes(pdf));
    }

    @Test
    @WithMockUser
    void unknownParcelReturns404() throws Exception {
        when(receiptService.getReceipt(anyString())).thenThrow(new ParcelNotFoundException("SDP0000000"));

        mockMvc.perform(get("/api/v1/receipts/SDP0000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Parcel not found"));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/receipts/SDP7845621"))
                .andExpect(status().isUnauthorized());
    }
}
