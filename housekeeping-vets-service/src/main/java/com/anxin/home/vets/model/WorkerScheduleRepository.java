package com.anxin.home.vets.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerScheduleRepository extends JpaRepository<WorkerSchedule, Integer> {
    List<WorkerSchedule> findByHousekeeperIdAndServiceDate(Integer housekeeperId, LocalDate serviceDate);
    @Query("select (count(s) > 0) from WorkerSchedule s where s.housekeeperId = :workerId " +
        "and s.serviceDate = :date and s.status <> 'AVAILABLE' and s.startTime < :endTime and s.endTime > :startTime")
    boolean existsBlockingOverlap(@Param("workerId") Integer housekeeperId, @Param("date") LocalDate serviceDate,
                                  @Param("endTime") LocalTime endTime, @Param("startTime") LocalTime startTime);
    Optional<WorkerSchedule> findByReservationKey(String reservationKey);
    void deleteByHousekeeperId(Integer housekeeperId);
}
