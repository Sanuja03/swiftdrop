package com.swiftdrop.backend.modules.receipt.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;
import com.swiftdrop.backend.modules.receipt.dto.ReceiptFile;
import com.swiftdrop.backend.modules.receipt.service.ReceiptService;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * Receipt download for signed-in customers. Receipts hold names and addresses,
 * so this endpoint is never public. Once accounts exist it must also check that
 * the parcel belongs to the current user.
 */
@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<byte[]> download(
            @PathVariable @Pattern(regexp = ParcelLookupService.TRACKING_NUMBER_PATTERN)
            String trackingNumber) {
        ReceiptFile receipt = receiptService.getReceipt(trackingNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(receipt.fileName()).build().toString())
                .body(receipt.content());
    }
}
