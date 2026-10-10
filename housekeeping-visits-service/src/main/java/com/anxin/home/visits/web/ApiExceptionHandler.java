package com.anxin.home.visits.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream().findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage()).orElse("请求参数不合法");
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), request);
    }
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "BOOKING_CONFLICT", e.getMessage(), request);
    }
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail persistenceConflict(Exception e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "BOOKING_CONFLICT", "Booking state changed or conflicts with another request", request);
    }
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException e, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", e.getMessage(), request);
    }
    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail dependency(RestClientResponseException e, HttpServletRequest request) {
        HttpStatus status = e.getStatusCode().value() == 409 ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE;
        return problem(status, status == HttpStatus.CONFLICT ? "WORKER_TIME_CONFLICT" : "DEPENDENCY_UNAVAILABLE",
            status == HttpStatus.CONFLICT ? "该服务人员在所选时间不可用" : "依赖服务暂时不可用", request);
    }
    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? status.getReasonPhrase() : detail);
        problem.setTitle(code);
        problem.setType(URI.create("urn:housekeeping:error:" + code.toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestId(request));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
    private String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value != null && value.matches("[A-Za-z0-9_-]{8,80}") ? value : UUID.randomUUID().toString();
    }
}
