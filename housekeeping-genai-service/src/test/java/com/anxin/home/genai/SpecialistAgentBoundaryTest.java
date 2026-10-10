package com.anxin.home.genai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialistAgentBoundaryTest {

    @Test
    void orchestratorOnlySeesThreeSpecialistDelegations() {
        assertEquals(Set.of(
            "delegateSolutionDesign",
            "delegateBookingCoordination",
            "delegateAfterSales"
        ), toolMethods(SpecialistAgents.class));
    }

    @Test
    void solutionAdvisorIsReadOnly() {
        Set<String> methods = toolMethods(SolutionAdvisorTools.class);
        assertEquals(Set.of(
            "listServiceCatalog",
            "listCustomerAddresses",
            "listCustomerBookingHistory"
        ), methods);
        assertFalse(methods.stream().anyMatch(name -> name.startsWith("prepare")));
    }

    @Test
    void bookingAndAfterSalesToolsAreSeparated() {
        Set<String> booking = toolMethods(BookingCoordinatorTools.class);
        Set<String> afterSales = toolMethods(AfterSalesTools.class);

        assertTrue(booking.contains("findAvailableHousekeepers"));
        assertTrue(booking.contains("calculateTrustedPrice"));
        assertTrue(booking.contains("prepareBooking"));
        assertFalse(booking.contains("prepareCancellation"));

        assertEquals(Set.of("listCustomerBookings", "prepareCancellation"), afterSales);
        assertFalse(afterSales.contains("prepareBooking"));
    }

    private Set<String> toolMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Tool.class))
            .map(Method::getName)
            .collect(Collectors.toSet());
    }
}
