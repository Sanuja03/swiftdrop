package com.swiftdrop.backend.modules.receipt.service;

import java.time.Clock;
import java.util.UUID;

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
 * Issues a parcel's receipt the first time its owner asks for it, then renders it.
 */
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ParcelLookupService parcelLookupService;
    private final ReceiptRepository receiptRepository;
    private final ReceiptRenderer receiptRenderer;
    private final Clock clock;

    /**
     * @throws com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException
     *         if the parcel does not exist or belongs to another customer
     */
    @Transactional
    public ReceiptFile getReceipt(String trackingNumber, UUID customerId) {
        Parcel parcel = parcelLookupService.getOwnedByCustomer(trackingNumber, customerId);
        Receipt receipt = receiptRepository.findByParcel_Id(parcel.getId())
                .orElseGet(() -> receiptRepository.save(Receipt.issueFor(parcel, clock.instant())));

        byte[] pdf = receiptRenderer.render(ReceiptData.of(receipt, parcel));
        return new ReceiptFile(receipt.getReceiptNumber() + ".pdf", pdf);
    }
}
