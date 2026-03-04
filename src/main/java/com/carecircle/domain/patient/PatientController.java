package com.carecircle.domain.patient;

import com.carecircle.domain.patient.dto.PatientDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// =============================================================================
// 🧠 CONTROLLER RULES (Senior Principles):
//
// Controllers are DUMB. They only do 3 things:
//   1. Parse the incoming HTTP request
//   2. Call the service
//   3. Return the HTTP response
//
// NO business logic here. No if-statements about data. No database calls.
// If you find yourself writing business logic in a controller, move it
// to the service. This makes your service testable without HTTP.
// =============================================================================

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/patients")
// 🧠 URL Design: Patients are always nested under an organization.
// /organizations/{orgId}/patients enforces that you can ONLY access
// patients within the context of their organization.
// This makes the tenant boundary explicit in the URL itself.
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    // POST /api/v1/organizations/{orgId}/patients
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)    // Returns 201 Created, not 200 OK
    public PatientDto.Response createPatient(
            @PathVariable UUID orgId,
            @Valid @RequestBody PatientDto.CreateRequest request
            // 🧠 @Valid triggers JSR-303 validation on the DTO.
            // If @NotBlank or @Past fails, Spring throws MethodArgumentNotValidException
            // BEFORE your service is ever called. The GlobalExceptionHandler
            // catches it and returns a clean 400 error.
    ) {
        return patientService.createPatient(orgId, request);
    }

    // GET /api/v1/organizations/{orgId}/patients?page=0&size=20&sort=fullName
    @GetMapping
    public Page<PatientDto.Summary> getPatients(
            @PathVariable UUID orgId,
            @PageableDefault(size = 20, sort = "fullName") Pageable pageable
            // 🧠 @PageableDefault: If the client doesn't specify pagination,
            // default to 20 results sorted by name.
            // NEVER return all rows. Always paginate. A hospital with 10,000
            // patients would crash your app if you loaded them all at once.
    ) {
        return patientService.getPatients(orgId, pageable);
    }

    // GET /api/v1/organizations/{orgId}/patients/{patientId}
    @GetMapping("/{patientId}")
    public PatientDto.Response getPatient(
            @PathVariable UUID orgId,
            @PathVariable UUID patientId
    ) {
        return patientService.getPatient(orgId, patientId);
    }

    // PUT /api/v1/organizations/{orgId}/patients/{patientId}
    @PutMapping("/{patientId}")
    public PatientDto.Response updatePatient(
            @PathVariable UUID orgId,
            @PathVariable UUID patientId,
            @Valid @RequestBody PatientDto.UpdateRequest request
    ) {
        return patientService.updatePatient(orgId, patientId, request);
    }

    // DELETE /api/v1/organizations/{orgId}/patients/{patientId}
    @DeleteMapping("/{patientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)  // 204 No Content = success, no body
    public void deactivatePatient(
            @PathVariable UUID orgId,
            @PathVariable UUID patientId
    ) {
        patientService.deactivatePatient(orgId, patientId);
    }
}


// =============================================================================
// FILE: src/main/java/com/carecircle/shared/exception/ResourceNotFoundException.java
// =============================================================================
// package com.carecircle.shared.exception;
//
// import java.util.UUID;
//
// public class ResourceNotFoundException extends RuntimeException {
//     public ResourceNotFoundException(String resource, UUID id) {
//         super(resource + " not found with id: " + id);
//     }
// }


// =============================================================================
// FILE: src/main/java/com/carecircle/shared/exception/GlobalExceptionHandler.java
// =============================================================================
// 🧠 @RestControllerAdvice: A single class that intercepts ALL exceptions
// thrown anywhere in your application and converts them to clean JSON.
//
// Without this: Spring returns a 500 HTML stack trace to the client.
// With this: Client always gets { "error": "...", "timestamp": "..." }
//
// package com.carecircle.shared.exception;
//
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.validation.FieldError;
// import org.springframework.web.bind.MethodArgumentNotValidException;
// import org.springframework.web.bind.annotation.ExceptionHandler;
// import org.springframework.web.bind.annotation.RestControllerAdvice;
// import java.time.Instant;
// import java.util.HashMap;
// import java.util.Map;
//
// @RestControllerAdvice
// public class GlobalExceptionHandler {
//
//     // Handles @Valid failures — returns which fields failed and why
//     @ExceptionHandler(MethodArgumentNotValidException.class)
//     public ResponseEntity<Map<String, Object>> handleValidationErrors(
//             MethodArgumentNotValidException ex) {
//         Map<String, String> fieldErrors = new HashMap<>();
//         ex.getBindingResult().getAllErrors().forEach(error -> {
//             String field = ((FieldError) error).getField();
//             String message = error.getDefaultMessage();
//             fieldErrors.put(field, message);
//         });
//         return ResponseEntity.badRequest().body(Map.of(
//             "error", "VALIDATION_FAILED",
//             "fields", fieldErrors,
//             "timestamp", Instant.now()
//         ));
//     }
//
//     // Handles patient/org not found
//     @ExceptionHandler(ResourceNotFoundException.class)
//     public ResponseEntity<Map<String, Object>> handleNotFound(
//             ResourceNotFoundException ex) {
//         return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
//             "error", "RESOURCE_NOT_FOUND",
//             "message", ex.getMessage(),
//             "timestamp", Instant.now()
//         ));
//     }
//
//     // Catch-all for unexpected errors
//     @ExceptionHandler(Exception.class)
//     public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
//         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
//             "error", "INTERNAL_ERROR",
//             "message", "An unexpected error occurred",
//             "timestamp", Instant.now()
//         ));
//     }
// }