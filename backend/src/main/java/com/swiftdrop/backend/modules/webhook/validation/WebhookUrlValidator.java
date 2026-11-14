package com.swiftdrop.backend.modules.webhook.validation;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.webhook.exception.WebhookException;

import lombok.RequiredArgsConstructor;

/** Public HTTP(S) destinations only. No localhost or development bypass is enabled. */
@Component
@RequiredArgsConstructor
public class WebhookUrlValidator {
    private final WebhookAddressResolver resolver;

    public String validate(String value) {
        if (value == null || value.isBlank() || value.length() > 500) {
            throw WebhookException.invalidDestination();
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw WebhookException.invalidDestination();
        }
        String host = uri.getHost();
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || host == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw WebhookException.invalidDestination();
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            host = host.substring(1, host.length() - 1);
        }
        // A trailing DNS dot must not bypass reserved-name checks.
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.contains("%") || (!host.contains(".") && !host.contains(":"))
                || reservedHost(host)) {
            throw WebhookException.invalidDestination();
        }
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses.length == 0) {
                throw WebhookException.invalidDestination();
            }
            // Reject mixed public/private answers as well as purely private hosts.
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw WebhookException.invalidDestination();
                }
            }
        } catch (UnknownHostException | SecurityException ex) {
            throw WebhookException.invalidDestination();
        }
        String asciiUrl = uri.toASCIIString();
        if (asciiUrl.length() > 500) {
            throw WebhookException.invalidDestination();
        }
        return asciiUrl;
    }

    private static boolean reservedHost(String host) {
        for (String suffix : new String[]{"localhost", "local", "internal", "lan", "home", "test",
                "invalid", "example", "onion", "arpa"}) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int a = bytes[0] & 255;
        int b = bytes[1] & 255;
        if (bytes.length == 4) {
            int c = bytes[2] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                    || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2)) || (b == 88 && c == 99)))
                    || (a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                    || (a == 203 && b == 0 && c == 113));
        }
        // Only global-unicast IPv6; exclude special-use/tunnelling/documentation ranges.
        return (a & 0xe0) == 0x20
                && !(a == 0x20 && b == 0x01 && ((bytes[2] & 255) < 2
                    || ((bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8)))
                && !(a == 0x20 && b == 0x02)
                && !(a == 0x3f && b == 0xff && (bytes[2] & 0xf0) == 0);
    }
}
