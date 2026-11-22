package com.swiftdrop.backend.modules.webhook.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.swiftdrop.backend.modules.webhook.exception.WebhookException;

class WebhookUrlValidatorTest {
    private final WebhookAddressResolver resolver = mock(WebhookAddressResolver.class);
    private final WebhookUrlValidator validator = new WebhookUrlValidator(resolver);

    @ParameterizedTest
    @ValueSource(strings = {"https://hooks.shop.com/events", "http://hooks.shop.com:8080/events?version=1"})
    void publicHttpDestinationsAreAccepted(String url) throws Exception {
        when(resolver.resolve("hooks.shop.com")).thenReturn(addresses("8.8.8.8", "2606:4700:4700::1111"));
        assertThat(validator.validate(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not a url", "/relative", "file:///etc/passwd", "ftp://shop.com/hook",
            "https://user:password@shop.com/hook", "https://shop.com/hook#fragment", "http:/missing-host",
            "http://shop.com:0/hook", "http://shop.com:65536/hook", "http://localhost/hook",
            "http://LOCALHOST./hook", "http://foo.localhost/hook", "http://service/hook",
            "http://service.local/hook", "http://metadata.google.internal/hook", "http://printer.lan/hook",
            "http://foo.home/hook", "http://foo.test/hook", "http://foo.invalid/hook", "http://foo.onion/hook",
            "http://[fe80::1%25eth0]/hook"})
    void malformedOrInternalNamesAreRejected(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(WebhookException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "0.1.2.3", "10.0.0.1", "127.0.0.1", "100.64.0.1",
            "169.254.169.254", "172.16.0.1", "172.31.255.255", "192.168.1.1", "192.0.0.1",
            "192.0.2.1", "192.88.99.1", "198.18.0.1", "198.51.100.1", "203.0.113.1",
            "224.0.0.1", "255.255.255.255", "::", "::1", "fc00::1", "fd00::1", "fe80::1",
            "ff02::1", "::ffff:127.0.0.1", "64:ff9b::a00:1", "2001:db8::1", "2002:a00:1::1",
            "2001::1", "3fff::1"})
    void internalAndSpecialUseDnsAnswersAreRejected(String address) throws Exception {
        when(resolver.resolve("hooks.shop.com")).thenReturn(addresses(address));
        assertThatThrownBy(() -> validator.validate("https://hooks.shop.com/events"))
                .isInstanceOf(WebhookException.class);
    }

    @Test
    void mixedDnsAnswersAndDnsFailureAreRejected() throws Exception {
        when(resolver.resolve("hooks.shop.com")).thenReturn(addresses("8.8.8.8", "10.0.0.1"));
        assertThatThrownBy(() -> validator.validate("https://hooks.shop.com/hook"))
                .isInstanceOf(WebhookException.class);
        when(resolver.resolve("hooks.shop.com")).thenThrow(new UnknownHostException("sensitive-input"));
        assertThatThrownBy(() -> validator.validate("https://hooks.shop.com/hook"))
                .isInstanceOf(WebhookException.class).hasMessageNotContaining("sensitive-input");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.1/hook", "http://[::1]/hook", "http://[::ffff:127.0.0.1]/hook"})
    void literalAddressVariantsAreRejected(String url) throws Exception {
        // Use the real resolver only for numeric literals (no external DNS).
        WebhookUrlValidator literalValidator = new WebhookUrlValidator(new WebhookAddressResolver());
        assertThatThrownBy(() -> literalValidator.validate(url)).isInstanceOf(WebhookException.class);
    }

    @Test
    void lengthLimitAppliesAfterAsciiEncoding() throws Exception {
        when(resolver.resolve("hooks.shop.com")).thenReturn(addresses("8.8.8.8"));
        assertThatThrownBy(() -> validator.validate("https://hooks.shop.com/" + "x".repeat(500)))
                .isInstanceOf(WebhookException.class);
        assertThatThrownBy(() -> validator.validate("https://hooks.shop.com/" + "é".repeat(100)))
                .isInstanceOf(WebhookException.class);
    }

    private static InetAddress[] addresses(String... values) throws Exception {
        InetAddress[] result = new InetAddress[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = InetAddress.getByName(values[i]);
        }
        return result;
    }
}
