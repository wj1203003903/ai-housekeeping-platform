package com.anxin.home.customers.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail missing(Exception e, HttpServletRequest r) { return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", e.getMessage(), r); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        String detail = e.getBindingResult().getFieldErrors().stream().findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage()).orElse("Invalid request");
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, r);
    }
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(Exception e, HttpServletRequest r) { return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", e.getMessage(), r); }
    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail == null ? status.getReasonPhrase() : detail);
        p.setTitle(code); p.setType(URI.create("urn:housekeeping:error:" + code.toLowerCase()));
        p.setInstance(URI.create(request.getRequestURI())); p.setProperty("code", code);
        p.setProperty("requestId", requestId(request)); p.setProperty("timestamp", Instant.now()); return p;
    }
    private String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value != null && value.matches("[A-Za-z0-9_-]{8,80}") ? value : UUID.randomUUID().toString();
    }
}
