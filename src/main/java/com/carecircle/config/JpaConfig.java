// ============================================================
// FILE 1: src/main/java/com/carecircle/config/JpaConfig.java
// ============================================================
// 🧠 Without @EnableJpaAuditing, the @CreatedDate and
// @LastModifiedDate annotations on BaseEntity do NOTHING.
// This one annotation activates the entire auditing system.

package com.carecircle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
    // No code needed — the annotation does all the work
}


// ============================================================
// ADD TO pom.xml (inside <dependencies>):
// ============================================================
// The hypersistence-utils library maps Java objects to PostgreSQL
// JSONB columns. Without this, Patient.metadata and
// OutboxEvent.payload won't work.
//
// <dependency>
//     <groupId>io.hypersistence</groupId>
//     <artifactId>hypersistence-utils-hibernate-63</artifactId>
//     <version>3.9.9</version>
// </dependency>
//
// ============================================================
// Also add to application.yml under spring.jpa.properties:
// ============================================================
//
//   properties:
//     hibernate:
//       type:
//         preferred_uuid_jdbc_type: VARCHAR  # Store UUIDs as VARCHAR in Neon