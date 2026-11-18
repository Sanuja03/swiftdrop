package com.swiftdrop.backend.modules.messaging.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the outbox relay and the webhook engine, bound from {@code app.messaging.*}.
 * Every value has a sensible default, so nothing has to be set to run locally.
 */
@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(@DefaultValue Outbox outbox, @DefaultValue Webhook webhook) {

    /**
     * @param publisherEnabled turns the scheduled relay on or off (the poll interval itself is
     *                         read by {@code @Scheduled} from {@code app.messaging.outbox.poll-interval-ms})
     * @param batchSize        rows relayed per database transaction
     * @param confirmTimeout   how long to wait for RabbitMQ to confirm each message
     * @param maxBackoff       longest wait before retrying a row that could not be published
     */
    public record Outbox(
            @DefaultValue("true") boolean publisherEnabled,
            @DefaultValue("50") int batchSize,
            @DefaultValue("5s") Duration confirmTimeout,
            @DefaultValue("5m") Duration maxBackoff) {
    }

    /**
     * @param retryDelays    wait before each retry; one retry queue is declared per distinct value,
     *                       and a delivery gets {@code retryDelays.size() + 1} attempts in total
     * @param connectTimeout TCP connect timeout for merchant endpoints
     * @param requestTimeout total time allowed for a merchant endpoint to answer
     */
    public record Webhook(
            @DefaultValue({"10s", "30s", "2m", "10m", "30m"}) List<Duration> retryDelays,
            @DefaultValue("3s") Duration connectTimeout,
            @DefaultValue("5s") Duration requestTimeout) {
    }
}
