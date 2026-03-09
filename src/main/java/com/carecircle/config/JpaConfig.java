package com.carecircle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 🧠 JavaTimeModule: Teaches Jackson how to serialize/deserialize
        // Java 8 date types — Instant, LocalDate, ZonedDateTime.
        // Without this, serializing Instant throws an exception.
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}