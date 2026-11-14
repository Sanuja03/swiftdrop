package com.swiftdrop.backend.modules.receipt.pdf;

/**
 * Turns receipt data into a downloadable document.
 */
public interface ReceiptRenderer {

    byte[] render(ReceiptData data);
}
