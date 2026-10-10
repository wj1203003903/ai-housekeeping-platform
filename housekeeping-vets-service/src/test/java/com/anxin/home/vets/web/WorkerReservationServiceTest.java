package com.anxin.home.vets.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.anxin.home.vets.model.Housekeeper;
import com.anxin.home.vets.model.HousekeeperRepository;
import com.anxin.home.vets.model.ServiceItem;
import com.anxin.home.vets.model.ServiceItemRepository;
import com.anxin.home.vets.model.ServiceSkill;
import com.anxin.home.vets.model.WorkerSchedule;
import com.anxin.home.vets.model.WorkerScheduleRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerReservationServiceTest {
    private final HousekeeperRepository housekeepers = mock(HousekeeperRepository.class);
    private final ServiceItemRepository serviceItems = mock(ServiceItemRepository.class);
    private final WorkerScheduleRepository schedules = mock(WorkerScheduleRepository.class);
    private WorkerReservationService service;
    private ReservationRequest request;

    @BeforeEach
    void setUp() {
        service = new WorkerReservationService(housekeepers, serviceItems, schedules);
        request = new ReservationRequest(1, LocalDate.now().plusDays(1), LocalTime.of(9, 0),
            LocalTime.of(11, 0), "浦东新区", "reservation-12345678");
        Housekeeper worker = mock(Housekeeper.class);
        ServiceItem item = mock(ServiceItem.class);
        ServiceSkill skill = mock(ServiceSkill.class);
        when(worker.getStatus()).thenReturn("AVAILABLE");
        when(worker.getServiceDistrict()).thenReturn("全市");
        when(worker.getSkills()).thenReturn(java.util.Set.of(skill));
        when(item.getSkill()).thenReturn(skill);
        when(skill.getId()).thenReturn(1);
        when(housekeepers.findByIdForUpdate(1)).thenReturn(Optional.of(worker));
        when(serviceItems.findById(1)).thenReturn(Optional.of(item));
    }

    @Test
    void rejectsOverlappingReservationWhileHoldingWorkerLock() {
        when(schedules.existsBlockingOverlap(
            1, request.serviceDate(), request.endTime(), request.startTime())).thenReturn(true);
        assertThrows(BookingConflictException.class, () -> service.reserve(1, request));
        verify(housekeepers).findByIdForUpdate(1);
        verify(schedules, never()).save(any());
    }

    @Test
    void savesNonOverlappingReservation() {
        when(schedules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.reserve(1, request);
        verify(schedules).save(any(WorkerSchedule.class));
    }
}
