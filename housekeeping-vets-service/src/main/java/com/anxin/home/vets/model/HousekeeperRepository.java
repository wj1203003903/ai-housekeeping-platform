package com.anxin.home.vets.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface HousekeeperRepository extends JpaRepository<Housekeeper, Integer> {
    List<Housekeeper> findByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Housekeeper h where h.id = :id")
    Optional<Housekeeper> findByIdForUpdate(@Param("id") Integer id);
}
