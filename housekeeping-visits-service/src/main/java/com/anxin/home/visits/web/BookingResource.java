package com.anxin.home.visits.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import com.anxin.home.visits.model.ServiceBooking;
import com.anxin.home.visits.model.ServiceBookingRepository;
import com.anxin.home.visits.model.ServiceReview;
import com.anxin.home.visits.model.ServiceReviewRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/bookings")
class BookingResource {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "PENDING_ASSIGNMENT", Set.of("ASSIGNED", "CANCELLED"),
        "ASSIGNED", Set.of("ACCEPTED", "CANCELLED"),
        "ACCEPTED", Set.of("IN_SERVICE", "CANCELLED"),
        "IN_SERVICE", Set.of("COMPLETED"),
        "COMPLETED", Set.of(),
        "CANCELLED", Set.of()
    );

    private final ServiceBookingRepository bookingRepository;
    private final ServiceReviewRepository reviewRepository;
    private final BookingReferenceClient referenceClient;
    private final BookingEventService eventService;

    BookingResource(ServiceBookingRepository bookingRepository, ServiceReviewRepository reviewRepository,
                    BookingReferenceClient referenceClient, BookingEventService eventService) {
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.referenceClient = referenceClient;
        this.eventService = eventService;
    }

    @GetMapping
    List<ServiceBooking> list(@RequestParam(required = false) Integer customerId,
                              @RequestParam(required = false) Integer housekeeperId,
                              Authentication authentication) {
        if (hasRole(authentication, "CUSTOMER")) {
            return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(requiredNumericSubject(authentication));
        }
        if (hasRole(authentication, "WORKER")) {
            return bookingRepository.findByHousekeeperIdOrderByCreatedAtDesc(requiredNumericSubject(authentication));
        }
        requireRole(authentication, "ADMIN", "INTERNAL");
        if (customerId != null) return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        if (housekeeperId != null) return bookingRepository.findByHousekeeperIdOrderByCreatedAtDesc(housekeeperId);
        return bookingRepository.findAll();
    }

    @GetMapping("/{bookingId}")
    ServiceBooking find(@PathVariable long bookingId, Authentication authentication) {
        ServiceBooking booking = load(bookingId);
        requireCanRead(authentication, booking);
        return booking;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ServiceBooking create(@Valid @RequestBody CreateBookingRequest request,
                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                          Authentication authentication) {
        requireRole(authentication, "CUSTOMER", "ADMIN", "INTERNAL");
        validateIdempotencyKey(idempotencyKey);
        String storedIdempotencyKey = scopedIdempotencyKey(authentication, idempotencyKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            ServiceBooking existing = bookingRepository.findByIdempotencyKey(storedIdempotencyKey).orElse(null);
            if (existing != null) {
                requireCanRead(authentication, existing);
                return existing;
            }
        }
        int customerId = hasRole(authentication, "CUSTOMER")
            ? requiredNumericSubject(authentication) : request.customerId();
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        if (request.serviceDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("不能预约过去的日期");
        }
        if (java.time.Duration.between(request.startTime(), request.endTime()).toHours() > 12) {
            throw new IllegalArgumentException("A booking cannot exceed 12 hours");
        }
        BookingReferenceClient.AddressSnapshot address = referenceClient.requireAddress(customerId, request.addressId());
        BookingReferenceClient.ServiceItemSnapshot service = referenceClient.requireServiceItem(request.serviceItemId());
        if (!service.enabled()) throw new IllegalArgumentException("服务项目已停用");
        BigDecimal quantity = request.quantity().max(service.minimumUnits());
        String reservationKey = request.housekeeperId() == null ? null
            : "booking-" + UUID.randomUUID().toString().replace("-", "");

        if (request.housekeeperId() != null) {
            referenceClient.reserve(request.housekeeperId(), new BookingReferenceClient.ReservationCommand(
                service.id(), request.serviceDate(), request.startTime(), request.endTime(), address.district(), reservationKey));
        }
        try {
            ServiceBooking booking = new ServiceBooking();
            booking.setIdempotencyKey(storedIdempotencyKey);
            booking.setReservationKey(reservationKey);
            booking.setBookingNumber("HS-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
            booking.setCustomerId(customerId);
            booking.setAddressId(request.addressId());
            booking.setServiceItemId(service.id());
            booking.setHousekeeperId(request.housekeeperId());
            booking.setServiceDate(request.serviceDate());
            booking.setStartTime(request.startTime());
            booking.setEndTime(request.endTime());
            booking.setQuantity(quantity);
            booking.setUnitPrice(service.unitPrice());
            booking.setTotalPrice(service.unitPrice().multiply(quantity));
            booking.setStatus(request.housekeeperId() == null ? "PENDING_ASSIGNMENT" : "ASSIGNED");
            booking.setCustomerRemark(request.customerRemark());
            booking.setCreatedAt(LocalDateTime.now());
            booking.setUpdatedAt(LocalDateTime.now());
            ServiceBooking saved = bookingRepository.saveAndFlush(booking);
            eventService.record("BookingCreated", saved);
            return saved;
        } catch (RuntimeException exception) {
            referenceClient.release(reservationKey);
            throw exception;
        }
    }

    @PostMapping("/{bookingId}/assign")
    @Transactional
    ServiceBooking assign(@PathVariable long bookingId, @Valid @RequestBody AssignRequest request,
                          Authentication authentication) {
        requireRole(authentication, "ADMIN", "INTERNAL");
        ServiceBooking booking = load(bookingId);
        BookingReferenceClient.AddressSnapshot address = referenceClient.requireAddress(
            booking.getCustomerId(), booking.getAddressId());
        String reservationKey = "booking-" + UUID.randomUUID().toString().replace("-", "");
        referenceClient.reserve(request.housekeeperId(), new BookingReferenceClient.ReservationCommand(
            booking.getServiceItemId(), booking.getServiceDate(), booking.getStartTime(), booking.getEndTime(),
            address.district(), reservationKey));
        try {
            booking.setHousekeeperId(request.housekeeperId());
            booking.setReservationKey(reservationKey);
            return changeStatus(booking, "ASSIGNED");
        } catch (RuntimeException exception) {
            referenceClient.release(reservationKey);
            throw exception;
        }
    }

    @PostMapping("/{bookingId}/accept")
    @Transactional
    ServiceBooking accept(@PathVariable long bookingId, Authentication authentication) {
        ServiceBooking booking = load(bookingId);
        requireAssignedWorkerOrRole(authentication, booking, "ADMIN", "INTERNAL");
        return changeStatus(booking, "ACCEPTED");
    }

    @PostMapping("/{bookingId}/start")
    @Transactional
    ServiceBooking start(@PathVariable long bookingId, Authentication authentication) {
        ServiceBooking booking = load(bookingId);
        requireAssignedWorkerOrRole(authentication, booking, "ADMIN", "INTERNAL");
        return changeStatus(booking, "IN_SERVICE");
    }

    @PostMapping("/{bookingId}/complete")
    @Transactional
    ServiceBooking complete(@PathVariable long bookingId, Authentication authentication) {
        ServiceBooking booking = load(bookingId);
        requireAssignedWorkerOrRole(authentication, booking, "ADMIN", "INTERNAL");
        return changeStatus(booking, "COMPLETED");
    }

    @PostMapping("/{bookingId}/cancel")
    @Transactional
    ServiceBooking cancel(@PathVariable long bookingId, Authentication authentication) {
        ServiceBooking booking = load(bookingId);
        requireCustomerOwnerOrRole(authentication, booking, "ADMIN", "INTERNAL");
        if ("IN_SERVICE".equals(booking.getStatus()) || "COMPLETED".equals(booking.getStatus())) {
            throw new IllegalStateException("已开始或已完成的订单不能取消");
        }
        ServiceBooking cancelled = changeStatus(booking, "CANCELLED");
        referenceClient.release(booking.getReservationKey());
        return cancelled;
    }

    @PostMapping("/{bookingId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ServiceReview review(@PathVariable long bookingId, @Valid @RequestBody ReviewRequest request,
                         Authentication authentication) {
        ServiceBooking booking = load(bookingId);
        requireCustomerOwnerOrRole(authentication, booking, "ADMIN", "INTERNAL");
        if (!"COMPLETED".equals(booking.getStatus())) {
            throw new IllegalStateException("只有已完成订单可以评价");
        }
        if (reviewRepository.existsByBookingId(bookingId)) throw new IllegalStateException("该订单已经评价");
        ServiceReview review = new ServiceReview();
        review.setBookingId(bookingId);
        review.setCustomerId(booking.getCustomerId());
        review.setHousekeeperId(booking.getHousekeeperId());
        review.setScore(request.score());
        review.setContent(request.content());
        review.setCreatedAt(LocalDateTime.now());
        ServiceReview saved = reviewRepository.save(review);
        eventService.record("BookingReviewed", booking);
        return saved;
    }

    private ServiceBooking load(long bookingId) {
        return bookingRepository.findById(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("订单不存在：" + bookingId));
    }

    private ServiceBooking changeStatus(ServiceBooking booking, String status) {
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(booking.getStatus(), Set.of());
        if (!allowed.contains(status)) {
            throw new IllegalStateException("订单不能从 " + booking.getStatus() + " 变更为 " + status);
        }
        booking.setStatus(status);
        booking.setUpdatedAt(LocalDateTime.now());
        ServiceBooking saved = bookingRepository.save(booking);
        eventService.record(switch (status) {
            case "ASSIGNED" -> "WorkerAssigned";
            case "ACCEPTED" -> "WorkerAccepted";
            case "IN_SERVICE" -> "ServiceStarted";
            case "COMPLETED" -> "ServiceCompleted";
            case "CANCELLED" -> "BookingCancelled";
            default -> "BookingStatusChanged";
        }, saved);
        return saved;
    }

    private void requireCanRead(Authentication authentication, ServiceBooking booking) {
        if (hasRole(authentication, "ADMIN") || hasRole(authentication, "INTERNAL")) return;
        if (hasRole(authentication, "CUSTOMER")
            && booking.getCustomerId().equals(requiredNumericSubject(authentication))) return;
        if (hasRole(authentication, "WORKER") && booking.getHousekeeperId() != null
            && booking.getHousekeeperId().equals(requiredNumericSubject(authentication))) return;
        throw new AccessDeniedException("当前账号无权读取该订单");
    }

    private void requireCustomerOwnerOrRole(Authentication auth, ServiceBooking booking, String... roles) {
        for (String role : roles) if (hasRole(auth, role)) return;
        if (hasRole(auth, "CUSTOMER") && booking.getCustomerId().equals(requiredNumericSubject(auth))) return;
        throw new AccessDeniedException("当前客户无权操作该订单");
    }

    private void requireAssignedWorkerOrRole(Authentication auth, ServiceBooking booking, String... roles) {
        for (String role : roles) if (hasRole(auth, role)) return;
        if (hasRole(auth, "WORKER") && booking.getHousekeeperId() != null
            && booking.getHousekeeperId().equals(requiredNumericSubject(auth))) return;
        throw new AccessDeniedException("订单未分配给当前服务人员");
    }

    private void requireRole(Authentication authentication, String... roles) {
        for (String role : roles) if (hasRole(authentication, role)) return;
        throw new AccessDeniedException("当前角色没有执行该操作的权限");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    private int requiredNumericSubject(Authentication authentication) {
        try { return Integer.parseInt(authentication.getName()); }
        catch (RuntimeException exception) { throw new AccessDeniedException("账号没有有效的业务身份"); }
    }

    private void validateIdempotencyKey(String key) {
        if (key != null && !key.isBlank() && !key.matches("[A-Za-z0-9_-]{8,80}")) {
            throw new IllegalArgumentException("无效的 Idempotency-Key");
        }
    }

    private String scopedIdempotencyKey(Authentication authentication, String key) {
        if (key == null || key.isBlank()) return null;
        try {
            String principal = authentication == null ? "anonymous" : authentication.getName();
            String role = authentication == null ? "NONE" : authentication.getAuthorities().toString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((principal + ":" + role + ":" + key).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not scope idempotency key", exception);
        }
    }
}

record CreateBookingRequest(
    @Positive int customerId,
    @Positive int addressId,
    @Positive int serviceItemId,
    @Positive Integer housekeeperId,
    @NotNull @FutureOrPresent LocalDate serviceDate,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal quantity,
    @Size(max = 500) String customerRemark
) {}

record AssignRequest(@NotNull @Positive Integer housekeeperId) {}

record ReviewRequest(@NotNull @Min(1) @Max(5) Integer score, @Size(max = 1000) String content) {}
