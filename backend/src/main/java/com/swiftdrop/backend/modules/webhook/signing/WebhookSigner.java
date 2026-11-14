package com.swiftdrop.backend.modules.webhook.signing;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Signs webhook bodies with HMAC-SHA256 so merchants can check a request really came
 * from SwiftDrop and was not changed or replayed.
 *
 * <p>Header format: {@code X-SwiftDrop-Signature: t=<unix seconds>,v1=<hex hmac>}, where the
 * HMAC is computed over {@code "<t>.<raw body>"} with the merchant's webhook secret.
 * Including the timestamp lets the merchant reject old, replayed requests.
 */
@Component
public class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-SwiftDrop-Signature";
    private static final String ALGORITHM = "HmacSHA256";

    public String sign(String secret, long timestampSeconds, String body) {
        return "t=" + timestampSeconds + ",v1=" + hmacHex(secret, timestampSeconds + "." + body);
    }

    /**
     * What a merchant does on their side; also used by the tests.
     *
     * @param tolerance how old the timestamp may be before the request counts as a replay
     */
    public boolean verify(String secret, String signatureHeader, String body, Instant now, Duration tolerance) {
        if (signatureHeader == null) {
            return false;
        }
        Long timestamp = null;
        String signature = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                timestamp = parseLong(pair[1]);
            } else if ("v1".equals(pair[0])) {
                signature = pair[1];
            }
        }
        if (timestamp == null || signature == null) {
            return false;
        }
        long age = Math.abs(now.getEpochSecond() - timestamp);
        if (age > tolerance.toSeconds()) {
            return false;
        }
        byte[] expected = hmacHex(secret, timestamp + "." + body).getBytes(StandardCharsets.US_ASCII);
        // Constant-time comparison, so response timing reveals nothing about the right value.
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            // HmacSHA256 is required on every JVM, and any non-empty key is valid.
            throw new IllegalStateException("Cannot compute webhook signature", ex);
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
