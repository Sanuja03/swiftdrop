package com.swiftdrop.backend.modules.receipt.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class OpenPdfReceiptRendererTest {

    private final OpenPdfReceiptRenderer renderer = new OpenPdfReceiptRenderer("Asia/Colombo");

    private final ReceiptData data = new ReceiptData(
            "RCT-SDP7845621", Instant.parse("2026-11-05T04:30:00Z"), "SDP7845621",
            "Sanuja Perera", "No. 415, Cotta Road, Colombo",
            "Nimal Silva", "No. 12, Galle Road, Dehiwala",
            "Standard (1-3 working days)", new BigDecimal("2.50"), "Delivered",
            new BigDecimal("1590.00"), "LKR");

    @Test
    void producesAOnePagePdfWithTheReceiptDetails() throws IOException {
        byte[] pdf = renderer.render(data);

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text)
                    .contains("RCT-SDP7845621")
                    .contains("SDP7845621")
                    .contains("Nimal Silva")
                    .contains("LKR 1,590.00")
                    .contains("2.5 kg")
                    .contains("5 Nov 2026, 10:00 AM");
        }
    }
}
