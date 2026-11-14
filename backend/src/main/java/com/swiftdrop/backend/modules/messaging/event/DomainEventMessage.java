package com.swiftdrop.backend.modules.messaging.event;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * The JSON body of every message on the {@code swiftdrop.events} exchange. Consumers
 * (webhooks, notifications) read {@code eventType} and turn {@code payload} into the
 * matching record, for example {@link ParcelStatusChangedPayload}.
 *
 * @param eventId unique per event; use it to ignore a message delivered twice
 */
public record DomainEventMessage(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        JsonNode payload) {
}
