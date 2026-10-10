package com.anxin.home.vets.web;

import com.anxin.home.vets.model.Housekeeper;
import com.anxin.home.vets.model.HousekeeperRepository;
import com.anxin.home.vets.model.ServiceItem;
import com.anxin.home.vets.model.ServiceItemRepository;
import com.anxin.home.vets.model.WorkerSchedule;
import com.anxin.home.vets.model.WorkerScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Service
class WorkerReservationService {
    private final HousekeeperRepository housekeepers;
    private final ServiceItemRepository serviceItems;
    private final WorkerScheduleRepository schedules;

    WorkerReservationService(HousekeeperRepository housekeepers, ServiceItemRepository serviceItems,
                             WorkerScheduleRepository schedules) {
        this.housekeepers = housekeepers;
        this.serviceItems = serviceItems;
        this.schedules = schedules;
    }

    @Transactional
    WorkerSchedule reserve(int housekeeperId, ReservationRequest request) {
        WorkerSchedule existing = schedules.findByReservationKey(request.reservationKey()).orElse(null);
        if (existing != null) {
            boolean sameReservation = existing.getHousekeeperId() == housekeeperId
                && existing.getServiceDate().equals(request.serviceDate())
                && existing.getStartTime().equals(request.startTime())
                && existing.getEndTime().equals(request.endTime());
            if (!sameReservation) throw new BookingConflictException("Reservation key was already used for another slot");
            return existing;
        }
        if (request.serviceDate() == null || request.serviceDate().isBefore(LocalDate.now())
            || request.startTime() == null || request.endTime() == null
            || !request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("Invalid reservation date or time range");
        }
        Housekeeper worker = housekeepers.findByIdForUpdate(housekeeperId)
            .orElseThrow(() -> new IllegalArgumentException("Housekeeper not found"));
        ServiceItem service = serviceItems.findById(request.serviceItemId())
            .orElseThrow(() -> new IllegalArgumentException("Service item not found"));
        if (!"AVAILABLE".equals(worker.getStatus())) throw new BookingConflictException("服务人员当前不可接单");
        if (worker.getSkills().stream().noneMatch(skill -> skill.getId().equals(service.getSkill().getId()))) {
            throw new BookingConflictException("服务人员不具备该项目技能");
        }
        if (request.district() != null && !request.district().isBlank()
            && !"全市".equals(worker.getServiceDistrict())
            && !worker.getServiceDistrict().contains(request.district())) {
            throw new BookingConflictException("服务人员不在该服务区域");
        }
        boolean overlap = schedules.existsBlockingOverlap(
            housekeeperId, request.serviceDate(), request.endTime(), request.startTime());
        if (overlap) throw new BookingConflictException("该服务人员在所选时间已有安排");
        WorkerSchedule schedule = new WorkerSchedule();
        schedule.setHousekeeperId(housekeeperId);
        schedule.setServiceDate(request.serviceDate());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setStatus("BOOKED");
        schedule.setReservationKey(request.reservationKey());
        return schedules.save(schedule);
    }

    @Transactional
    void release(String reservationKey) {
        schedules.findByReservationKey(reservationKey).ifPresent(schedules::delete);
    }
}

record ReservationRequest(@Positive int serviceItemId,
                          @NotNull @FutureOrPresent LocalDate serviceDate,
                          @NotNull LocalTime startTime,
                          @NotNull LocalTime endTime,
                          @Size(max = 100) String district,
                          @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") String reservationKey) {
}

class BookingConflictException extends RuntimeException {
    BookingConflictException(String message) { super(message); }
}
