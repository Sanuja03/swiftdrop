package com.swiftdrop.backend.modules.messaging.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftdrop.backend.modules.messaging.entity.OutboxEvent;
import com.swiftdrop.backend.modules.messaging.entity.OutboxStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the oldest due rows for the current transaction. {@code SKIP LOCKED} lets several
     * backend instances relay at the same time without picking the same rows.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND available_at <= :now
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("now") Instant now, @Param("limit") int limit);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAt(UUID aggregateId);

    long countByStatus(OutboxStatus status);
}
