package com.carecircle.domain.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

public class OrganizationDto {

    @Getter @Setter @NoArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Organization name is required")
        private String name;
        private String planType = "FREE";
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private String name;
        private String planType;
        private Instant createdAt;
    }
}
