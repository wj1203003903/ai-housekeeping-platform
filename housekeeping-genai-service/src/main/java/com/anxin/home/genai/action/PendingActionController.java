package com.anxin.home.genai.action;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.anxin.home.genai.security.UserContext;
import com.anxin.home.genai.security.UserContextResolver;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/actions")
@Validated
public class PendingActionController {
    private final PendingActionService service;
    private final UserContextResolver resolver;

    public PendingActionController(PendingActionService service, UserContextResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @PostMapping("/{token}/confirm")
    Map<String, Object> confirm(
        @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String token,
        @AuthenticationPrincipal Jwt jwt) {
        UserContext context = resolver.resolve(jwt, "action-confirmation");
        return service.confirm(token, context);
    }
}
