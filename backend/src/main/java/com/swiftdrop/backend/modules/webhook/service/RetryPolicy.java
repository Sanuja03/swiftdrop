package com.swiftdrop.backend.modules.webhook.service;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.messaging.config.MessagingProperties;

/**
 * How many times a webhook is tried and how long to wait between tries.
 * With the default delays a delivery is tried 6 times over roughly 43 minutes.
 */
@Component
public class RetryPolicy {

    private final List<Duration> delays;

    public RetryPolicy(MessagingProperties properties) {
        this.delays = List.copyOf(properties.webhook().retryDelays());
    }

    /** The first attempt plus one per configured delay. */
    public int maxAttempts() {
        return delays.size() + 1;
    }

    /**
     * @param attemptsMade attempts already made, starting at 1
     * @return how long to wait before the next attempt
     * @throws IllegalArgumentException if no attempts are left
     */
    public Duration delayAfter(int attemptsMade) {
        if (attemptsMade < 1 || attemptsMade > delays.size()) {
            throw new IllegalArgumentException("No retry is planned after attempt " + attemptsMade);
        }
        return delays.get(attemptsMade - 1);
    }
}
