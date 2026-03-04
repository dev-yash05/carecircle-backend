 package com.carecircle.shared.exception;

 import org.springframework.http.HttpStatus;
 import org.springframework.http.ResponseEntity;
 import org.springframework.validation.FieldError;
 import org.springframework.web.bind.MethodArgumentNotValidException;
 import org.springframework.web.bind.annotation.ExceptionHandler;
 import org.springframework.web.bind.annotation.RestControllerAdvice;
 import java.time.Instant;
 import java.util.HashMap;
 import java.util.Map;

 @RestControllerAdvice
 public class GlobalExceptionHandler {

     // Handles @Valid failures — returns which fields failed and why
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
             "error", "VALIDATION_FAILED",
             "fields", fieldErrors,
             "timestamp", Instant.now()
         ));
     }

     // Handles patient/org not found
     @ExceptionHandler(ResourceNotFoundException.class)
     public ResponseEntity<Map<String, Object>> handleNotFound(
             ResourceNotFoundException ex) {
         return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
             "error", "RESOURCE_NOT_FOUND",
             "message", ex.getMessage(),
             "timestamp", Instant.now()
         ));
     }

     // Catch-all for unexpected errors
     @ExceptionHandler(Exception.class)
     public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
             "error", "INTERNAL_ERROR",
             "message", ex.getMessage(),
             "timestamp", Instant.now()
         ));
     }
 }