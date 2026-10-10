package com.anxin.home.customers.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceAddressRepository extends JpaRepository<ServiceAddress, Integer> {
    List<ServiceAddress> findByCustomerId(Integer customerId);
    void deleteByCustomerId(Integer customerId);
}
