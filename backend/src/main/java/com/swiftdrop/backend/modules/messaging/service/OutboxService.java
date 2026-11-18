package com.swiftdrop.backend.modules.messaging.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.messaging.entity.OutboxEvent;
import com.swiftdrop.backend.modules.messaging.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * The single way to publish a domain event. Any module can call {@link #publishEvent};
 * the event reaches RabbitMQ only if the caller's transaction commits.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * Saves an event to the outbox as part of the caller's transaction.
     * {@code MANDATORY} makes it fail loudly if called outside one, because then the event
     * and the business change could no longer succeed or fail together.
     *
     * @param routingKey RabbitMQ routing key on the {@code swiftdrop.events} exchange
     * @param payload    any object Jackson can write as JSON
     * @return the saved row; its id is the event id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publishEvent(String aggregateType, UUID aggregateId, String eventType,
                                    String routingKey, Object payload) {
        String json = jsonMapper.writeValueAsString(payload);
        return repository.save(OutboxEvent.pending(aggregateType, aggregateId, eventType, routingKey, json,
                clock.instant()));
    }
}
