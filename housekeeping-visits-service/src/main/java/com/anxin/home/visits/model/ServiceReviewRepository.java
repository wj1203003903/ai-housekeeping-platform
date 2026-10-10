package com.anxin.home.visits.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceReviewRepository extends JpaRepository<ServiceReview, Long> {
    boolean existsByBookingId(Long bookingId);
}
