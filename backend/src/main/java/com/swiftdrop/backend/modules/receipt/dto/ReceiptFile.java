package com.swiftdrop.backend.modules.receipt.dto;

/**
 * A rendered receipt ready to download.
 */
public record ReceiptFile(String fileName, byte[] content) {
}
