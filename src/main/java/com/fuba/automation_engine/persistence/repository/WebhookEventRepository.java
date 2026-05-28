package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, Long> {

    boolean existsBySourceAndEventId(WebhookSource source, String eventId);

    boolean existsBySourceAndPayloadHash(WebhookSource source, String payloadHash);

    List<WebhookEventEntity> findByStatusOrderByReceivedAtAsc(WebhookEventStatus status);

    Optional<WebhookEventEntity> findBySourceAndEventId(WebhookSource source, String eventId);

    @Query("SELECT DISTINCT w.eventType FROM WebhookEventEntity w WHERE w.eventType IS NOT NULL ORDER BY w.eventType ASC")
    List<String> findDistinctEventTypes();

    /**
     * Top 10 most recent webhook events for a person, ordered by received_at
     * then id (both DESC). Used by the persons detail endpoint's timeline
     * aggregation.
     */
    List<WebhookEventEntity> findTop10BySourcePersonIdOrderByReceivedAtDescIdDesc(String sourcePersonId);
}
