package com.swiftdrop.backend.modules.receipt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;
import com.swiftdrop.backend.modules.receipt.dto.ReceiptFile;
import com.swiftdrop.backend.modules.receipt.entity.Receipt;
import com.swiftdrop.backend.modules.receipt.pdf.ReceiptData;
import com.swiftdrop.backend.modules.receipt.pdf.ReceiptRenderer;
import com.swiftdrop.backend.modules.receipt.repository.ReceiptRepository;
import com.swiftdrop.backend.support.TestParcels;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};

    @Mock
    private ParcelLookupService parcelLookupService;
    @Mock
    private ReceiptRepository receiptRepository;
    @Mock
    private ReceiptRenderer receiptRenderer;

    private ReceiptService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        service = new ReceiptService(parcelLookupService, receiptRepository, receiptRenderer,
                Clock.fixed(NOW, ZoneOffset.UTC));
        parcel = TestParcels.withId(UUID.randomUUID());
        when(parcelLookupService.getByTrackingNumber(TestParcels.TRACKING_NUMBER)).thenReturn(parcel);
        when(receiptRenderer.render(any())).thenReturn(PDF);
    }

    @Test
    void issuesAReceiptTheFirstTime() {
        when(receiptRepository.findByParcel_Id(parcel.getId())).thenReturn(Optional.empty());
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(call -> call.getArgument(0));

        ReceiptFile file = service.getReceipt(TestParcels.TRACKING_NUMBER);

        ArgumentCaptor<Receipt> saved = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(saved.capture());
        assertThat(saved.getValue().getReceiptNumber()).isEqualTo("RCT-SDP7845621");
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("590.00"));
        assertThat(saved.getValue().getCurrency()).isEqualTo("LKR");
        assertThat(saved.getValue().getIssuedAt()).isEqualTo(NOW);

        assertThat(file.fileName()).isEqualTo("RCT-SDP7845621.pdf");
        assertThat(file.content()).isEqualTo(PDF);
    }

    @Test
    void reusesAnExistingReceipt() {
        Receipt existing = Receipt.issueFor(parcel, Instant.parse("2026-11-01T08:00:00Z"));
        when(receiptRepository.findByParcel_Id(parcel.getId())).thenReturn(Optional.of(existing));

        service.getReceipt(TestParcels.TRACKING_NUMBER);

        verify(receiptRepository, never()).save(any());
        ArgumentCaptor<ReceiptData> data = ArgumentCaptor.forClass(ReceiptData.class);
        verify(receiptRenderer).render(data.capture());
        assertThat(data.getValue().issuedAt()).isEqualTo(existing.getIssuedAt());
        assertThat(data.getValue().pickupAddress()).isEqualTo("No. 415, Cotta Road, Colombo");
    }
}
