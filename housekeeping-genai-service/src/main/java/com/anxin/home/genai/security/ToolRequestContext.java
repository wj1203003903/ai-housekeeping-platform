package com.anxin.home.genai.security;

import com.anxin.home.genai.action.PendingAction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ToolRequestContext {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private ToolRequestContext() {
    }

    public static UserContext require() {
        State state = CURRENT.get();
        if (state == null) {
            throw new IllegalStateException("No authenticated AI request context is available");
        }
        return state.context;
    }

    public static void registerPendingAction(PendingAction action) {
        State state = CURRENT.get();
        if (state == null) {
            throw new IllegalStateException("No authenticated AI request context is available");
        }
        if (!state.context.userId().equals(action.userId())) {
            throw new SecurityException("Pending action does not belong to the authenticated user");
        }
        state.pendingActions.add(action);
    }

    public static <T> T with(UserContext context, Supplier<T> action) {
        return capture(context, action).value();
    }

    public static <T> Captured<T> capture(UserContext context, Supplier<T> action) {
        State previous = CURRENT.get();
        State current = new State(context);
        CURRENT.set(current);
        try {
            return new Captured<>(action.get(), List.copyOf(current.pendingActions));
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public record Captured<T>(T value, List<PendingAction> pendingActions) {
    }

    private static final class State {
        private final UserContext context;
        private final List<PendingAction> pendingActions = new ArrayList<>();

        private State(UserContext context) {
            this.context = context;
        }
    }
}
