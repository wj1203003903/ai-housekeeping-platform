package com.anxin.home.genai.memory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.anxin.home.genai.security.UserContextResolver;
import com.anxin.home.genai.security.ComplianceGuard;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/memory")
public class MemoryController {
    private final LongTermMemoryService service;
    private final UserContextResolver resolver;
    private final ComplianceGuard complianceGuard;

    public MemoryController(LongTermMemoryService service, UserContextResolver resolver,
                            ComplianceGuard complianceGuard) {
        this.service = service;
        this.resolver = resolver;
        this.complianceGuard = complianceGuard;
    }

    @GetMapping
    List<UserMemory> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(resolver.currentUserId(jwt));
    }

    @PostMapping
    UserMemory remember(@Valid @RequestBody MemoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        complianceGuard.validateInput(request.content());
        return service.remember(resolver.currentUserId(jwt), request.kind(), request.content());
    }

    @DeleteMapping("/{id}")
    void forget(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.forget(resolver.currentUserId(jwt), id);
    }

    record MemoryRequest(@NotBlank @Size(max = 30) String kind,
                         @NotBlank @Size(max = 2000) String content) {}
}
