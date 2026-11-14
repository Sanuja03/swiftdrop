package com.swiftdrop.backend.modules.webhook.signing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String BODY = "{\"id\":\"1\",\"type\":\"parcel.status_changed\"}";
    private static final long TIMESTAMP = 1_794_565_800L;
    private static final Instant SIGNED_AT = Instant.ofEpochSecond(TIMESTAMP);
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void signatureHasTimestampAndKnownHmac() {
        // Computed independently: printf '1794565800.<body>' | openssl dgst -sha256 -hmac whsec_test_secret
        assertThat(signer.sign(SECRET, TIMESTAMP, BODY))
                .isEqualTo("t=1794565800,v1=79a791f6a18bface5bb8fefaf60ec2a4509f2c1fa4d49c128ebff6ea5ada1067");
    }

    @Test
    void sameInputGivesSameSignature() {
        assertThat(signer.sign(SECRET, TIMESTAMP, BODY)).isEqualTo(signer.sign(SECRET, TIMESTAMP, BODY));
    }

    @Test
    void validSignatureVerifies() {
        String header = signer.sign(SECRET, TIMESTAMP, BODY);

        assertThat(signer.verify(SECRET, header, BODY, SIGNED_AT.plusSeconds(30), TOLERANCE)).isTrue();
    }

    @Test
    void changedBodyIsRejected() {
        String header = signer.sign(SECRET, TIMESTAMP, BODY);

        assertThat(signer.verify(SECRET, header, BODY.replace("1", "2"), SIGNED_AT, TOLERANCE)).isFalse();
    }

    @Test
    void wrongSecretIsRejected() {
        String header = signer.sign(SECRET, TIMESTAMP, BODY);

        assertThat(signer.verify("another_secret", header, BODY, SIGNED_AT, TOLERANCE)).isFalse();
    }

    @Test
    void oldRequestIsRejectedAsReplay() {
        String header = signer.sign(SECRET, TIMESTAMP, BODY);

        assertThat(signer.verify(SECRET, header, BODY, SIGNED_AT.plus(Duration.ofMinutes(6)), TOLERANCE)).isFalse();
    }

    @Test
    void malformedHeadersAreRejected() {
        assertThat(signer.verify(SECRET, null, BODY, SIGNED_AT, TOLERANCE)).isFalse();
        assertThat(signer.verify(SECRET, "", BODY, SIGNED_AT, TOLERANCE)).isFalse();
        assertThat(signer.verify(SECRET, "t=abc,v1=00", BODY, SIGNED_AT, TOLERANCE)).isFalse();
        assertThat(signer.verify(SECRET, "v1=00", BODY, SIGNED_AT, TOLERANCE)).isFalse();
    }
}
