package com.carecircle.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// =============================================================================
// 🧠 @RestControllerAdvice: Intercepts ALL exceptions thrown anywhere in the
// application and converts them to clean JSON responses.
//
// Handler resolution order: Spring picks the MOST SPECIFIC handler first.
// So IllegalArgumentException is caught before the generic Exception catch-all.
//
// Why NOT let Spring's default error handling do this?
// Spring's default returns a 500 HTML page with a stack trace.
// That leaks implementation details to clients and breaks JSON API contracts.
// =============================================================================

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 400 Bad Request — @Valid field failures ───────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });
        return ResponseEntity.badRequest().body(Map.of(
                "error",     "VALIDATION_FAILED",
                "fields",    fieldErrors,
                "timestamp", Instant.now()
        ));
    }

    // ── 400 Bad Request — invalid arguments (bad enum value, bad month format, etc.)
    // 🧠 IllegalArgumentException is thrown by our services when the client sends
    // a valid request body that fails business rules (e.g. unknown vital type,
    // invalid status transition). It's a client error → 400, not a server error.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        log.debug("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error",     "BAD_REQUEST",
                "message",   ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error",     "RESOURCE_NOT_FOUND",
                "message",   ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    // ── 403 Forbidden — @PreAuthorize failures ────────────────────────────────
    // 🧠 AccessDeniedException is thrown by Spring Security when a user is
    // authenticated but lacks the required role (@PreAuthorize("hasRole('ADMIN')")).
    // Without this handler, Spring Security redirects to /error (HTML 403 page).
    // We catch it here to return a clean JSON 403 instead.
    //
    // IMPORTANT: This must be declared BEFORE the generic Exception handler,
    // AND Spring Security's ExceptionTranslationFilter must be configured to
    // let the exception propagate to @RestControllerAdvice. By default in
    // Spring Security 6, AccessDeniedException IS propagated for REST APIs
    // (no session, stateless config) — so no extra config is needed here.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error",     "ACCESS_DENIED",
                "message",   "You do not have permission to perform this action.",
                "timestamp", Instant.now()
        ));
    }

    // ── 409 Conflict — illegal state transitions ──────────────────────────────
    // 🧠 IllegalStateException is thrown when a valid resource exists but
    // the requested operation is not valid for its current state.
    // Examples: marking a dose that is already TAKEN, optimistic lock conflict.
    // 409 Conflict is the correct HTTP status for "the resource exists but
    // the operation conflicts with its current state."
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error",     "CONFLICT",
                "message",   ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    // ── 500 Internal Server Error — catch-all ─────────────────────────────────
    // 🧠 Only reached for truly unexpected exceptions (NPE, DB connection loss,
    // programming bugs). We log the full stack trace here (the only place we
    // do so) and return a generic message — never expose internal details.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error",     "INTERNAL_ERROR",
                "message",   "An unexpected error occurred. Please try again later.",
                "timestamp", Instant.now()
        ));
    }
}