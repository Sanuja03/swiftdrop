package com.swiftdrop.backend.modules.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftdrop.backend.modules.messaging.config.MessagingProperties;
import com.swiftdrop.backend.modules.messaging.config.MessagingTopology;
import com.swiftdrop.backend.modules.messaging.entity.OutboxEvent;
import com.swiftdrop.backend.modules.messaging.entity.OutboxStatus;
import com.swiftdrop.backend.modules.messaging.repository.OutboxEventRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-13T09:30:00Z");

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RabbitOperations operations;
    @Mock
    private ConnectionFactory connectionFactory;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        MessagingProperties properties = new MessagingProperties(
                new MessagingProperties.Outbox(true, 50, Duration.ofSeconds(5), Duration.ofMinutes(5)),
                new MessagingProperties.Webhook(List.of(Duration.ofSeconds(10)), Duration.ofSeconds(3),
                        Duration.ofSeconds(5)));
        publisher = new OutboxPublisher(repository, rabbitTemplate, transactionTemplate, jsonMapper, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        lenient().when(rabbitTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        lenient().when(connectionFactory.isSimplePublisherConfirms()).thenReturn(true);
        lenient().when(rabbitTemplate.invoke(any())).thenAnswer(invocation ->
                invocation.<RabbitOperations.OperationsCallback<?>>getArgument(0).doInRabbit(operations));
    }

    @Test
    void publishesEnvelopeToEventsExchangeWaitsForConfirmAndMarksPublished() {
        OutboxEvent event = pendingEvent();
        when(repository.lockNextBatch(NOW, 50)).thenReturn(List.of(event));

        int relayed = publisher.relayBatch();

        assertThat(relayed).isEqualTo(1);
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(operations).send(eq(MessagingTopology.EVENTS_EXCHANGE), eq("parcel.status.in_transit"), sent.capture());
        verify(operations).waitForConfirmsOrDie(5000);

        Message message = sent.getValue();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.getId().toString());
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);

        JsonNode body = jsonMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(body.get("eventId").asString()).isEqualTo(event.getId().toString());
        assertThat(body.get("eventType").asString()).isEqualTo("parcel.status_changed");
        assertThat(body.get("payload").get("trackingNumber").asString()).isEqualTo("SDP7845621");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void brokerFailureKeepsRowPendingAndBacksOff() {
        OutboxEvent event = pendingEvent();
        when(repository.lockNextBatch(NOW, 50)).thenReturn(List.of(event));
        doThrow(new AmqpException("Connection refused")).when(operations).send(any(), any(), any(Message.class));

        publisher.relayBatch();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPublishAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Connection refused");
        assertThat(event.getAvailableAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void oneFailedRowDoesNotStopTheRestOfTheBatch() {
        OutboxEvent failing = pendingEvent();
        OutboxEvent healthy = pendingEvent();
        when(repository.lockNextBatch(NOW, 50)).thenReturn(List.of(failing, healthy));
        doThrow(new AmqpException("Channel closed"))
                .doNothing()
                .when(operations).send(any(), any(), any(Message.class));

        publisher.relayBatch();

        assertThat(failing.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(healthy.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void backoffDoublesAndIsCapped() {
        assertThat(publisher.backoffAfter(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(publisher.backoffAfter(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(publisher.backoffAfter(5)).isEqualTo(Duration.ofSeconds(32));
        assertThat(publisher.backoffAfter(30)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void keepsRelayingWhileBatchesAreFull() {
        when(transactionTemplate.execute(any())).thenReturn(50, 50, 7);

        publisher.publishPending();

        verify(transactionTemplate, times(3)).execute(any());
    }

    @Test
    void doesNotWaitForConfirmsWhenConfirmsAreOff() {
        when(connectionFactory.isSimplePublisherConfirms()).thenReturn(false);
        when(repository.lockNextBatch(any(), anyInt())).thenReturn(List.of(pendingEvent()));

        publisher.relayBatch();

        verify(operations, never()).waitForConfirmsOrDie(anyLong());
    }

    private static OutboxEvent pendingEvent() {
        return OutboxEvent.pending("parcel", UUID.randomUUID(), "parcel.status_changed", "parcel.status.in_transit",
                "{\"trackingNumber\":\"SDP7845621\",\"status\":\"IN_TRANSIT\"}", NOW.minusSeconds(1));
    }
}
