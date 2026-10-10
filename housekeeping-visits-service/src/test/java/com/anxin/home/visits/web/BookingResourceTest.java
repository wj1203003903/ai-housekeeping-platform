package com.anxin.home.visits.web;

import org.junit.jupiter.api.Test;
import com.anxin.home.visits.model.ServiceBooking;
import com.anxin.home.visits.model.ServiceBookingRepository;
import com.anxin.home.visits.model.ServiceReviewRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BookingResourceTest {
    private final ServiceBookingRepository bookings = mock(ServiceBookingRepository.class);
    private final ServiceReviewRepository reviews = mock(ServiceReviewRepository.class);
    private final BookingReferenceClient references = mock(BookingReferenceClient.class);
    private final BookingEventService events = mock(BookingEventService.class);
    private final BookingResource resource = new BookingResource(bookings, reviews, references, events);

    @Test
    void returnsExistingBookingForRepeatedIdempotencyKey() {
        ServiceBooking existing = new ServiceBooking();
        when(bookings.findByIdempotencyKey(anyString())).thenReturn(Optional.of(existing));

        ServiceBooking result = resource.create(new CreateBookingRequest(0, 0, 0, null,
            null, null, null, null, null), "action-token-1234", auth("internal", "INTERNAL"));

        assertSame(existing, result);
    }

    @Test
    void rejectsIllegalStateTransition() {
        ServiceBooking booking = new ServiceBooking();
        booking.setStatus("ASSIGNED");
        when(bookings.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class, () -> resource.start(1L, auth("internal", "INTERNAL")));
        verifyNoInteractions(reviews);
    }

    @Test
    void calculatesPriceFromTrustedServiceCatalog() {
        when(references.requireAddress(1, 1)).thenReturn(new BookingReferenceClient.AddressSnapshot(1, "浦东新区"));
        when(references.requireServiceItem(1)).thenReturn(new BookingReferenceClient.ServiceItemSnapshot(
            1, "日常保洁", new BigDecimal("50.00"), new BigDecimal("2.00"), 120, true));
        when(bookings.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceBooking booking = resource.create(new CreateBookingRequest(999, 1, 1, null,
            LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(11, 0),
            BigDecimal.ONE, "test"), null, auth("1", "CUSTOMER"));

        assertEquals(1, booking.getCustomerId());
        assertEquals(new BigDecimal("50.00"), booking.getUnitPrice());
        assertEquals(new BigDecimal("100.0000"), booking.getTotalPrice());
    }

    @Test
    void customerCannotCancelAnotherCustomersBooking() {
        ServiceBooking booking = new ServiceBooking();
        booking.setCustomerId(2);
        booking.setStatus("ASSIGNED");
        when(bookings.findById(1L)).thenReturn(Optional.of(booking));
        assertThrows(AccessDeniedException.class, () -> resource.cancel(1L, auth("1", "CUSTOMER")));
    }

    private Authentication auth(String subject, String role) {
        return new UsernamePasswordAuthenticationToken(subject, "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
