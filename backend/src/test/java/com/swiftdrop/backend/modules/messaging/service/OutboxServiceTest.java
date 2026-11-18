package com.swiftdrop.backend.modules.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.backend.modules.messaging.entity.OutboxEvent;
import com.swiftdrop.backend.modules.messaging.entity.OutboxStatus;
import com.swiftdrop.backend.modules.messaging.repository.OutboxEventRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T09:30:00Z");

    @Mock
    private OutboxEventRepository repository;

    private OutboxService service;

    @BeforeEach
    void setUp() {
        service = new OutboxService(repository, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void savesEventAsPendingJsonRowThatIsDueImmediately() {
        UUID parcelId = UUID.randomUUID();

        OutboxEvent saved = service.publishEvent("parcel", parcelId, "parcel.status_changed",
                "parcel.status.in_transit", new SamplePayload("SDP7845621", "IN_TRANSIT"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAggregateType()).isEqualTo("parcel");
        assertThat(saved.getAggregateId()).isEqualTo(parcelId);
        assertThat(saved.getEventType()).isEqualTo("parcel.status_changed");
        assertThat(saved.getRoutingKey()).isEqualTo("parcel.status.in_transit");
        assertThat(saved.getPayload()).isEqualTo("{\"trackingNumber\":\"SDP7845621\",\"status\":\"IN_TRANSIT\"}");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getAvailableAt()).isEqualTo(NOW);
        assertThat(saved.getPublishAttempts()).isZero();
    }

    @Test
    void everyEventGetsItsOwnId() {
        OutboxEvent first = service.publishEvent("parcel", UUID.randomUUID(), "t", "k", new SamplePayload("a", "b"));
        OutboxEvent second = service.publishEvent("parcel", UUID.randomUUID(), "t", "k", new SamplePayload("a", "b"));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    record SamplePayload(String trackingNumber, String status) {
    }
}
