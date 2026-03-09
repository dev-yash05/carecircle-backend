//🧠 THE OUTBOX PUBLISHER — The "relay" between DB and RabbitMQ
//
// This runs every 5 seconds. It reads PENDING outbox events and
// publishes them to RabbitMQ. Once published, marks them PROCESSED.
//
// If RabbitMQ is down:
//   - The event stays PENDING in the DB
//   - Next poll (5 seconds later) retries automatically
//   - After 3 retries, marks as FAILED (for manual investigation)
//
// This guarantees at-least-once delivery of every notification.

package com.carecircle.scheduler;

import com.carecircle.domain.outbox.OutboxEvent;
import com.carecircle.domain.outbox.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public static final String EXCHANGE = "carecircle.events";
    public static final String DOSE_ROUTING_KEY = "dose.event";

    // Runs every 5 seconds
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) return;

        log.debug("OutboxPublisher: Processing {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Convert payload to JSON string
                String message = objectMapper.writeValueAsString(event.getPayload());

                // Publish to RabbitMQ
                // Exchange: carecircle.events
                // Routing key: dose.event (notification-module subscribes to this)
                rabbitTemplate.convertAndSend(EXCHANGE, DOSE_ROUTING_KEY, message);

                // Mark as processed
                event.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);

                log.info("Published outbox event: {} type: {}",
                        event.getId(), event.getEventType());

            } catch (Exception e) {
                // RabbitMQ is down or serialization failed
                log.error("Failed to publish outbox event: {}", event.getId(), e);

                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());

                // After 3 retries, mark as FAILED for manual investigation
                if (event.getRetryCount() >= 3) {
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                    log.error("Outbox event {} permanently failed after 3 retries", event.getId());
                }

                outboxEventRepository.save(event);
            }
        }
    }
}