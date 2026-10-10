package com.anxin.home.visits.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import com.anxin.home.visits.model.DomainEventOutbox;
import com.anxin.home.visits.model.DomainEventOutboxRepository;
import com.anxin.home.visits.model.ServiceBooking;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
class BookingEventService {
    private static final Logger LOG = LoggerFactory.getLogger(BookingEventService.class);
    private final DomainEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher publisher;

    BookingEventService(DomainEventOutboxRepository repository, ObjectMapper objectMapper,
                        ApplicationEventPublisher publisher) {
        this.repository = repository; this.objectMapper = objectMapper; this.publisher = publisher;
    }

    void record(String eventType, ServiceBooking booking) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "bookingId", booking.getId(), "bookingNumber", booking.getBookingNumber(),
                "customerId", booking.getCustomerId(), "housekeeperId", booking.getHousekeeperId() == null ? 0 : booking.getHousekeeperId(),
                "status", booking.getStatus(), "occurredAt", java.time.Instant.now().toString()));
            repository.save(new DomainEventOutbox(String.valueOf(booking.getId()), eventType, payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize booking event", exception);
        }
    }

    @Scheduled(fixedDelayString = "${housekeeping.outbox.publish-delay:1000}")
    @Transactional
    public void publishPending() {
        for (DomainEventOutbox event : repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            publisher.publishEvent(new BookingDomainEvent(event.getEventType(), event.getAggregateId(), event.getPayload()));
            LOG.info("booking-event type={} aggregateId={} payload={}", event.getEventType(), event.getAggregateId(), event.getPayload());
            event.markPublished();
        }
    }
}

record BookingDomainEvent(String type, String aggregateId, String payload) {}
