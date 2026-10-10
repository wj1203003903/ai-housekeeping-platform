package com.anxin.home.visits.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {
    List<ServiceBooking> findByCustomerIdOrderByCreatedAtDesc(Integer customerId);
    List<ServiceBooking> findByHousekeeperIdOrderByCreatedAtDesc(Integer housekeeperId);
    Optional<ServiceBooking> findByIdempotencyKey(String idempotencyKey);
}
