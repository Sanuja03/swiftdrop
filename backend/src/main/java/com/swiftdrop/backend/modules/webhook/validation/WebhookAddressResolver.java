package com.swiftdrop.backend.modules.webhook.validation;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.stereotype.Component;

/** Separate DNS boundary so validation tests do not rely on live DNS. */
@Component
public class WebhookAddressResolver {
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
