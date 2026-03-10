package com.carecircle.domain.audit.dto;

import lombok.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AuditLogDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private String action;
        private String entityType;
        private UUID entityId;
        private String actorEmail;
        private Map<String, Object> newValue;
        private Map<String, Object> oldValue;
        private Instant createdAt;
    }
}