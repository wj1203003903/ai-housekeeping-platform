package com.anxin.home.vets.web;

import org.springframework.http.HttpStatus;
import com.anxin.home.vets.model.Housekeeper;
import com.anxin.home.vets.model.HousekeeperRepository;
import com.anxin.home.vets.model.ServiceItem;
import com.anxin.home.vets.model.ServiceItemRepository;
import com.anxin.home.vets.model.ServiceSkill;
import com.anxin.home.vets.model.ServiceSkillRepository;
import com.anxin.home.vets.model.WorkerSchedule;
import com.anxin.home.vets.model.WorkerScheduleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@RestController
@Validated
class HousekeepingResource {

    private final HousekeeperRepository housekeeperRepository;
    private final ServiceSkillRepository skillRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final WorkerScheduleRepository scheduleRepository;
    private final WorkerReservationService reservationService;

    HousekeepingResource(HousekeeperRepository housekeeperRepository,
                         ServiceSkillRepository skillRepository,
                         ServiceItemRepository serviceItemRepository,
                         WorkerScheduleRepository scheduleRepository,
                         WorkerReservationService reservationService) {
        this.housekeeperRepository = housekeeperRepository;
        this.skillRepository = skillRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.scheduleRepository = scheduleRepository;
        this.reservationService = reservationService;
    }

    @GetMapping("/housekeepers")
    List<?> housekeepers(Authentication authentication) {
        List<Housekeeper> workers = housekeeperRepository.findAll();
        if (hasRole(authentication, "ADMIN") || hasRole(authentication, "INTERNAL")) return workers;
        return workers.stream()
            .filter(worker -> "AVAILABLE".equals(worker.getStatus())
                || hasRole(authentication, "WORKER")
                && Integer.toString(worker.getId()).equals(authentication.getName()))
            .map(HousekeeperView::from).toList();
    }

    @PostMapping("/housekeepers")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Housekeeper createHousekeeper(@Valid @RequestBody HousekeeperRequest request, Authentication authentication) {
        requireRole(authentication, "ADMIN", "INTERNAL");
        Housekeeper worker = new Housekeeper();
        worker.setName(request.name());
        worker.setTelephone(request.telephone());
        worker.setIntroduction(request.introduction());
        worker.setExperienceYears(request.experienceYears());
        worker.setRating(BigDecimal.valueOf(5));
        worker.setStatus(hasRole(authentication, "INTERNAL") ? "PENDING_REVIEW" : "AVAILABLE");
        worker.setServiceDistrict(request.serviceDistrict());
        List<Integer> skillIds = request.skillIds().stream().distinct().toList();
        List<ServiceSkill> skills = skillRepository.findAllById(skillIds);
        if (skills.size() != skillIds.size()) throw new IllegalArgumentException("One or more service skills do not exist");
        worker.getSkills().addAll(skills);
        return housekeeperRepository.save(worker);
    }

    @PostMapping("/housekeepers/{housekeeperId}/approve")
    Housekeeper approve(@PathVariable int housekeeperId, Authentication authentication) {
        requireRole(authentication, "ADMIN");
        Housekeeper worker = housekeeperRepository.findById(housekeeperId).orElseThrow();
        if (!"PENDING_REVIEW".equals(worker.getStatus())) {
            throw new IllegalStateException("Only pending workers can be approved");
        }
        worker.setStatus("AVAILABLE");
        return housekeeperRepository.save(worker);
    }

    @GetMapping("/housekeepers/{housekeeperId}")
    Object housekeeper(@PathVariable @Positive int housekeeperId, Authentication authentication) {
        Housekeeper worker = housekeeperRepository.findById(housekeeperId).orElseThrow();
        if (hasRole(authentication, "ADMIN") || hasRole(authentication, "INTERNAL")
            || hasRole(authentication, "WORKER") && Integer.toString(housekeeperId).equals(authentication.getName())) {
            return worker;
        }
        if (!"AVAILABLE".equals(worker.getStatus())) throw new AccessDeniedException("Worker profile is not public");
        return HousekeeperView.from(worker);
    }

    @DeleteMapping("/housekeepers/{housekeeperId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void deleteHousekeeper(@PathVariable int housekeeperId, Authentication authentication) {
        requireRole(authentication, "INTERNAL");
        Housekeeper worker = housekeeperRepository.findById(housekeeperId).orElseThrow();
        if (!"PENDING_REVIEW".equals(worker.getStatus())) {
            throw new IllegalStateException("Only unapproved registration profiles can be deleted");
        }
        scheduleRepository.deleteByHousekeeperId(housekeeperId);
        housekeeperRepository.delete(worker);
    }

    @GetMapping("/housekeepers/available")
    List<HousekeeperView> available(@RequestParam @Positive int serviceItemId,
                                    @RequestParam @FutureOrPresent LocalDate date,
                                    @RequestParam @NotNull LocalTime startTime,
                                    @RequestParam(required = false) @Size(max = 100) String district) {
        ServiceItem item = serviceItemRepository.findById(serviceItemId).orElseThrow();
        return housekeeperRepository.findByStatus("AVAILABLE").stream()
            .filter(worker -> district == null || district.isBlank()
                || worker.getServiceDistrict().contains(district)
                || "全市".equals(worker.getServiceDistrict()))
            .filter(worker -> worker.getSkills().stream().anyMatch(skill -> skill.getId().equals(item.getSkill().getId())))
            .filter(worker -> scheduleRepository.findByHousekeeperIdAndServiceDate(worker.getId(), date).stream()
                .noneMatch(slot -> !"AVAILABLE".equals(slot.getStatus())
                    && !slot.getEndTime().isBefore(startTime)
                    && !slot.getStartTime().isAfter(startTime)))
            .map(HousekeeperView::from).toList();
    }

    @GetMapping("/service-skills")
    List<ServiceSkill> skills() {
        return skillRepository.findAll();
    }

    @GetMapping("/service-items")
    List<ServiceItem> serviceItems() {
        return serviceItemRepository.findByEnabledTrue();
    }

    @GetMapping("/service-items/{serviceItemId}")
    ServiceItem serviceItem(@PathVariable int serviceItemId) {
        return serviceItemRepository.findById(serviceItemId).filter(ServiceItem::isEnabled).orElseThrow();
    }

    @PostMapping("/housekeepers/{housekeeperId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    WorkerSchedule reserve(@PathVariable @Positive int housekeeperId, @Valid @RequestBody ReservationRequest request,
                           Authentication authentication) {
        requireRole(authentication, "INTERNAL", "ADMIN");
        return reservationService.reserve(housekeeperId, request);
    }

    @PostMapping("/housekeepers/reservations/{reservationKey}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_-]{8,80}") String reservationKey,
                 Authentication authentication) {
        requireRole(authentication, "INTERNAL", "ADMIN");
        reservationService.release(reservationKey);
    }

    @PostMapping("/housekeepers/{housekeeperId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    WorkerSchedule addSchedule(@PathVariable @Positive int housekeeperId,
                               @Valid @RequestBody ManualScheduleRequest request,
                               Authentication authentication) {
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "INTERNAL")
            && !(hasRole(authentication, "WORKER") && Integer.toString(housekeeperId).equals(authentication.getName()))) {
            throw new AccessDeniedException("Cannot update another worker's schedule");
        }
        housekeeperRepository.findById(housekeeperId).orElseThrow();
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (scheduleRepository.existsBlockingOverlap(housekeeperId, request.serviceDate(),
            request.endTime(), request.startTime())) {
            throw new BookingConflictException("Schedule overlaps an existing blocked period");
        }
        WorkerSchedule schedule = new WorkerSchedule();
        schedule.setHousekeeperId(housekeeperId);
        schedule.setServiceDate(request.serviceDate());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setStatus("UNAVAILABLE");
        schedule.setReservationKey(null);
        return scheduleRepository.save(schedule);
    }

    private void requireRole(Authentication authentication, String... roles) {
        for (String role : roles) if (hasRole(authentication, role)) return;
        throw new AccessDeniedException("Insufficient permission");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}

record HousekeeperRequest(@NotBlank @Size(max = 50) String name,
                          @NotBlank @Size(max = 30) String telephone,
                          @Size(max = 500) String introduction,
                          @Min(0) @Max(60) int experienceYears,
                          @NotBlank @Size(max = 100) String serviceDistrict,
                          @NotEmpty @Size(max = 20) List<@Positive Integer> skillIds) {
}

record ManualScheduleRequest(@NotNull @FutureOrPresent LocalDate serviceDate,
                             @NotNull LocalTime startTime,
                             @NotNull LocalTime endTime) {}

record HousekeeperView(Integer id, String name, String introduction, Integer experienceYears,
                       BigDecimal rating, String status, String serviceDistrict, List<ServiceSkill> skills) {
    static HousekeeperView from(Housekeeper worker) {
        return new HousekeeperView(worker.getId(), worker.getName(), worker.getIntroduction(),
            worker.getExperienceYears(), worker.getRating(), worker.getStatus(), worker.getServiceDistrict(),
            List.copyOf(worker.getSkills()));
    }
}
