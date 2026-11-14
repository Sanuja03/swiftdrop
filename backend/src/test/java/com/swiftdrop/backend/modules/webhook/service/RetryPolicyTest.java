package com.swiftdrop.backend.modules.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swiftdrop.backend.modules.messaging.config.MessagingProperties;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(new MessagingProperties(
            new MessagingProperties.Outbox(true, 50, Duration.ofSeconds(5), Duration.ofMinutes(5)),
            new MessagingProperties.Webhook(
                    List.of(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2)),
                    Duration.ofSeconds(3), Duration.ofSeconds(5))));

    @Test
    void firstAttemptPlusOnePerDelay() {
        assertThat(policy.maxAttempts()).isEqualTo(4);
    }

    @Test
    void delaysAreUsedInOrder() {
        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void noDelayAfterLastAttempt() {
        assertThatThrownBy(() -> policy.delayAfter(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.delayAfter(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
