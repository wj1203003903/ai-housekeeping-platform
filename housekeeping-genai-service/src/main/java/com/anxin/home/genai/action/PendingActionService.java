package com.anxin.home.genai.action;

import com.anxin.home.genai.AIDataProvider;
import com.anxin.home.genai.audit.AuditService;
import com.anxin.home.genai.security.ToolRequestContext;
import com.anxin.home.genai.security.UserContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PendingActionService {
    private final PendingActionStore store;
    private final AIDataProvider dataProvider;
    private final AuditService auditService;

    public PendingActionService(PendingActionStore store, AIDataProvider dataProvider, AuditService auditService) {
        this.store = store;
        this.dataProvider = dataProvider;
        this.auditService = auditService;
    }

    public Map<String, Object> prepare(UserContext context, PendingAction.Type type, Map<String, Object> payload) {
        PendingAction action = store.create(context.userId(), type, payload);
        ToolRequestContext.registerPendingAction(action);
        auditService.record(context.userId(), context.conversationId(), "ACTION_PREPARED", "PENDING", type.name());
        return Map.of(
            "requiresConfirmation", true,
            "action", action.type().name(),
            "expiresAt", action.expiresAt(),
            "message", "操作尚未执行，请让用户在页面中确认"
        );
    }

    public Map<String, Object> confirm(String token, UserContext context) {
        PendingAction action = store.consume(token, context.userId())
            .orElseThrow(() -> new IllegalArgumentException("待确认操作不存在、已过期或不属于当前用户"));
        try {
            Map<String, Object> result = switch (action.type()) {
                case CREATE_BOOKING -> createBooking(action);
                case CANCEL_BOOKING -> dataProvider.cancelBooking(asLong(action.payload().get("bookingId")));
            };
            auditService.record(context.userId(), context.conversationId(), action.type().name(), "SUCCESS",
                "resultId=" + safeResultIdentifier(result));
            return result;
        } catch (RuntimeException exception) {
            auditService.record(context.userId(), context.conversationId(), action.type().name(), "FAILED", exception.getMessage());
            throw exception;
        }
    }

    private Map<String, Object> createBooking(PendingAction action) {
        Map<String, Object> payload = new LinkedHashMap<>(action.payload());
        int serviceItemId = Integer.parseInt(payload.get("serviceItemId").toString());
        BigDecimal quantity = new BigDecimal(payload.get("quantity").toString());
        Map<String, Object> quote = dataProvider.calculatePrice(serviceItemId, quantity);
        payload.put("quantity", quote.get("quantity"));
        payload.put("unitPrice", quote.get("unitPrice"));
        return dataProvider.createBooking(payload, action.token());
    }

    private long asLong(Object value) { return Long.parseLong(value.toString()); }

    private String safeResultIdentifier(Map<String, Object> result) {
        Object value = result.get("bookingNumber");
        if (value == null) value = result.get("id");
        return value == null ? "none" : value.toString().replaceAll("[^A-Za-z0-9_-]", "");
    }
}
