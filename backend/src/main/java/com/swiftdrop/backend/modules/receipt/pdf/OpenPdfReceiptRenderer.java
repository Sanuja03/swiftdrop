package com.swiftdrop.backend.modules.receipt.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Renders receipts as PDF using OpenPDF, in the SwiftDrop brand colours.
 */
@Component
public class OpenPdfReceiptRenderer implements ReceiptRenderer {

    private static final Color NAVY = new Color(0x0F, 0x27, 0x47);
    private static final Color ORANGE = new Color(0xE0, 0x6A, 0x1F);
    private static final Color TEXT = new Color(0x1E, 0x29, 0x3B);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color BORDER = new Color(0xE2, 0xE8, 0xF0);
    private static final Color SURFACE = new Color(0xF5, 0xF7, 0xFA);

    private static final Font BRAND = font(22, Font.BOLD, Color.WHITE);
    private static final Font HEADER_LABEL = font(10, Font.NORMAL, new Color(0xCB, 0xD5, 0xE1));
    private static final Font HEADER_VALUE = font(11, Font.BOLD, Color.WHITE);
    private static final Font SECTION = font(12, Font.BOLD, NAVY);
    private static final Font LABEL = font(9, Font.NORMAL, MUTED);
    private static final Font VALUE = font(10, Font.NORMAL, TEXT);
    private static final Font TOTAL_LABEL = font(12, Font.BOLD, TEXT);
    private static final Font TOTAL_VALUE = font(16, Font.BOLD, ORANGE);
    private static final Font FOOTER = font(8, Font.NORMAL, MUTED);

    private final DateTimeFormatter dateFormat;

    public OpenPdfReceiptRenderer(@Value("${app.timezone:Asia/Colombo}") String timezone) {
        this.dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
                .withZone(ZoneId.of(timezone));
    }

    @Override
    public byte[] render(ReceiptData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("SwiftDrop receipt " + data.receiptNumber());
            document.open();
            document.add(header(data));
            document.add(spacer());
            document.add(section("Shipment"));
            document.add(details(
                    "Tracking number", data.trackingNumber(),
                    "Service", data.serviceLabel(),
                    "Weight", formatWeight(data.weightKg()),
                    "Status", data.statusLabel()));
            document.add(spacer());
            document.add(section("Sender and recipient"));
            document.add(details(
                    "From", data.senderName(),
                    "Pickup address", data.pickupAddress(),
                    "To", data.recipientName(),
                    "Drop-off address", data.dropoffAddress()));
            document.add(spacer());
            document.add(total(data));
            document.add(spacer());
            document.add(new Paragraph("Thank you for shipping with SwiftDrop. "
                    + "This receipt was generated electronically and is valid without a signature.", FOOTER));
        } catch (DocumentException ex) {
            throw new ReceiptRenderingException("Could not render receipt " + data.receiptNumber(), ex);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private PdfPTable header(ReceiptData data) {
        PdfPTable table = new PdfPTable(new float[]{1.2f, 1f});
        table.setWidthPercentage(100);

        PdfPCell brand = cell(NAVY, 18);
        brand.addElement(new Paragraph("SwiftDrop", BRAND));
        brand.addElement(new Paragraph("Payment receipt", HEADER_LABEL));
        table.addCell(brand);

        PdfPCell meta = cell(NAVY, 18);
        meta.addElement(rightAligned("Receipt no.", HEADER_LABEL));
        meta.addElement(rightAligned(data.receiptNumber(), HEADER_VALUE));
        meta.addElement(rightAligned("Issued " + dateFormat.format(data.issuedAt()), HEADER_LABEL));
        table.addCell(meta);
        return table;
    }

    private static Paragraph section(String title) {
        Paragraph paragraph = new Paragraph(title, SECTION);
        paragraph.setSpacingAfter(6);
        return paragraph;
    }

    /** Two-column grid of label/value pairs. */
    private static PdfPTable details(String... labelsAndValues) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        for (int i = 0; i < labelsAndValues.length; i += 2) {
            PdfPCell cell = cell(Color.WHITE, 8);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(BORDER);
            cell.addElement(new Paragraph(labelsAndValues[i], LABEL));
            cell.addElement(new Paragraph(labelsAndValues[i + 1], VALUE));
            table.addCell(cell);
        }
        return table;
    }

    private PdfPTable total(ReceiptData data) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        PdfPCell label = cell(SURFACE, 14);
        label.addElement(new Paragraph("Total paid", TOTAL_LABEL));
        table.addCell(label);

        PdfPCell amount = cell(SURFACE, 14);
        amount.addElement(rightAligned(formatMoney(data.currency(), data.amount()), TOTAL_VALUE));
        table.addCell(amount);
        return table;
    }

    private static PdfPCell cell(Color background, float padding) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(background);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(padding);
        return cell;
    }

    private static Paragraph rightAligned(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        return paragraph;
    }

    private static Paragraph spacer() {
        Paragraph paragraph = new Paragraph(new Phrase(" "));
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    private static String formatMoney(String currency, BigDecimal amount) {
        return currency + " " + new DecimalFormat("#,##0.00").format(amount);
    }

    private static String formatWeight(BigDecimal weightKg) {
        return new DecimalFormat("0.##").format(weightKg) + " kg";
    }

    private static Font font(float size, int style, Color color) {
        return new Font(Font.HELVETICA, size, style, color);
    }
}
