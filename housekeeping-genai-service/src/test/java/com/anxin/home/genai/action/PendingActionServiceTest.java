package com.anxin.home.genai.action;

import org.junit.jupiter.api.Test;
import com.anxin.home.genai.AIDataProvider;
import com.anxin.home.genai.audit.AuditService;
import com.anxin.home.genai.security.ToolRequestContext;
import com.anxin.home.genai.security.UserContext;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendingActionServiceTest {

    @Test
    void prepareCapturesTokenForTheControllerWithoutReturningItToTheModel() {
        PendingActionStore store = mock(PendingActionStore.class);
        AIDataProvider dataProvider = mock(AIDataProvider.class);
        AuditService auditService = mock(AuditService.class);
        PendingActionService service = new PendingActionService(store, dataProvider, auditService);
        UserContext user = new UserContext("1", UserContext.Role.CUSTOMER, "1:test");
        PendingAction action = new PendingAction("action-token", "1", PendingAction.Type.CREATE_BOOKING,
            Map.of("customerId", 1), Instant.now().plusSeconds(600));
        when(store.create("1", PendingAction.Type.CREATE_BOOKING, action.payload())).thenReturn(action);

        ToolRequestContext.Captured<Map<String, Object>> captured = ToolRequestContext.capture(user,
            () -> service.prepare(user, PendingAction.Type.CREATE_BOOKING, action.payload()));

        assertFalse(captured.value().containsKey("actionToken"));
        assertEquals(true, captured.value().get("requiresConfirmation"));
        assertEquals(action, captured.pendingActions().get(0));
    }
}
