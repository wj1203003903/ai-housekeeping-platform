package com.anxin.home.genai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.anxin.home.genai.action.PendingAction;
import com.anxin.home.genai.action.PendingActionService;
import com.anxin.home.genai.security.ToolRequestContext;
import com.anxin.home.genai.security.UserContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HousekeepingToolsSecurityTest {
    private AIDataProvider dataProvider;
    private PendingActionService pendingActions;
    private HousekeepingTools tools;

    @BeforeEach
    void setUp() {
        dataProvider = mock(AIDataProvider.class);
        pendingActions = mock(PendingActionService.class);
        tools = new HousekeepingTools(dataProvider, pendingActions);
    }

    @Test
    void workerCannotReadArbitraryCustomerData() {
        UserContext worker = new UserContext("7", UserContext.Role.WORKER, "7:test");
        assertThrows(SecurityException.class,
            () -> ToolRequestContext.with(worker, () -> tools.listCustomerAddresses(1)));
        verifyNoInteractions(dataProvider);
    }

    @Test
    void customerCannotPrepareCancellationForAnotherCustomersBooking() {
        when(dataProvider.getBooking(99)).thenReturn(Map.of("id", 99, "customerId", 2));
        UserContext customer = new UserContext("1", UserContext.Role.CUSTOMER, "1:test");
        assertThrows(SecurityException.class,
            () -> ToolRequestContext.with(customer, () -> tools.prepareCancellation(99)));
        verifyNoInteractions(pendingActions);
    }

    @Test
    void customerCanPrepareCancellationForOwnBooking() {
        when(dataProvider.getBooking(11)).thenReturn(Map.of("id", 11, "customerId", 1));
        when(pendingActions.prepare(any(), eq(PendingAction.Type.CANCEL_BOOKING), any()))
            .thenReturn(Map.of("requiresConfirmation", true));
        UserContext customer = new UserContext("1", UserContext.Role.CUSTOMER, "1:test");

        Map<String, Object> result = ToolRequestContext.with(customer, () -> tools.prepareCancellation(11));

        assertEquals(true, result.get("requiresConfirmation"));
    }
}
