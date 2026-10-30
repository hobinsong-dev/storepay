package dev.hobin.storepay.webhook.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    boolean existsByEventId(String eventId);
}
