package com.swiftdrop.backend.modules.receipt.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;
import com.swiftdrop.backend.modules.receipt.dto.ReceiptFile;
import com.swiftdrop.backend.modules.receipt.entity.Receipt;
import com.swiftdrop.backend.modules.receipt.pdf.ReceiptData;
import com.swiftdrop.backend.modules.receipt.pdf.ReceiptRenderer;
import com.swiftdrop.backend.modules.receipt.repository.ReceiptRepository;

import lombok.RequiredArgsConstructor;

/**
 * Issues a parcel's receipt the first time it is asked for, then renders it.
 */
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ParcelLookupService parcelLookupService;
    private final ReceiptRepository receiptRepository;
    private final ReceiptRenderer receiptRenderer;
    private final Clock clock;

    @Transactional
    public ReceiptFile getReceipt(String trackingNumber) {
        Parcel parcel = parcelLookupService.getByTrackingNumber(trackingNumber);
        Receipt receipt = receiptRepository.findByParcel_Id(parcel.getId())
                .orElseGet(() -> receiptRepository.save(Receipt.issueFor(parcel, clock.instant())));

        byte[] pdf = receiptRenderer.render(ReceiptData.of(receipt, parcel));
        return new ReceiptFile(receipt.getReceiptNumber() + ".pdf", pdf);
    }
}
