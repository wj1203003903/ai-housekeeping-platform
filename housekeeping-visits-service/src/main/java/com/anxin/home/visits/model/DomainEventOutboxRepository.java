package com.anxin.home.visits.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DomainEventOutboxRepository extends JpaRepository<DomainEventOutbox, Long> {
    List<DomainEventOutbox> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
