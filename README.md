# 🏥 CareCircle Backend

> **Production-grade family caregiving platform.**  
> Spring Boot 4 · PostgreSQL · Redis · RabbitMQ · Next.js (planned) · 8-week build

[![Progress](https://img.shields.io/badge/Progress-Sprint%204%20Complete-brightgreen)](https://github.com/dev-yash05/carecircle-backend)
[![Stack](https://img.shields.io/badge/Stack-Spring%20Boot%204%20%7C%20PostgreSQL%20%7C%20Redis%20%7C%20RabbitMQ-blue)](https://github.com/dev-yash05/carecircle-backend)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://github.com/dev-yash05/carecircle-backend)

---

## 📖 Table of Contents

- [Project Overview](#-project-overview)
- [The Problem We Solve](#-the-problem-we-solve)
- [Real-World Use Case](#-real-world-use-case-a-morning-with-grandma)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Project Structure (Actual)](#-project-structure-actual)
- [Database Schema](#-database-schema)
- [API Endpoints (Implemented)](#-api-endpoints-implemented)
- [Progress Tracker](#-progress-tracker)
- [Sprint 1 — Foundation](#-sprint-1--foundation--infrastructure-week-1--complete)
- [Sprint 2 — Core APIs](#-sprint-2--core-apis-week-2--complete)
- [Sprint 3 — Security](#-sprint-3--security-week-3--complete)
- [Sprint 4 — Medication Engine](#-sprint-4--medication-engine-week-4--95-complete)
- [Sprint 5 — Performance](#-sprint-5--performance-week-5--upcoming)
- [Sprint 6 — Product Features](#-sprint-6--product-features-week-6--upcoming)
- [Sprint 7 — Production](#-sprint-7--production-week-78--upcoming)
- [Key Engineering Decisions](#-key-engineering-decisions-hiring-signals)
- [Getting Started](#-getting-started)
- [Environment Variables](#-environment-variables)
- [Continuing in a New Chat](#-continuing-in-a-new-chat)

---

## 🎯 Project Overview

**CareCircle** is a production-grade remote caregiving coordination platform. It enables family members to monitor and coordinate care for elderly relatives — tracking medication schedules, health vitals, and caregiver activity in real time, across devices.

Every architectural decision is chosen to demonstrate **senior-level engineering judgment**: distributed systems patterns, data integrity guarantees, security best practices, and observable production infrastructure.

| Metric | Value |
|--------|-------|
| Total Sprints | 7 |
| Timeline | 8 Weeks |
| **Current Status** | **Sprint 5 — Performance (starting now)** |
| Overall Progress | ~85% of core backend — Sprints 1–4 complete |
| Repository | [dev-yash05/carecircle-backend](https://github.com/dev-yash05/carecircle-backend) |
| Spring Boot | 4.0.3 |
| Java | 21 |

---

## 🧩 The Problem We Solve

Families with elderly dependents face a coordination nightmare:
- Caregivers don't always know **what medication** to give, **when**
- Family members in remote cities have **no real-time visibility** into care
- Missed doses, wrong timings, and lack of documentation lead to health crises
- There is **no reliable audit trail** if something goes wrong

CareCircle solves this with a scheduled, event-driven, real-time system that keeps every stakeholder in sync.

---

## 🌅 Real-World Use Case: "A Morning with Grandma"

> *Anjali (daughter, Bangalore) and Ramesh (caregiver, Sagar) caring for Grandma*

| Time | Actor | Event | Tech Behind It |
|------|-------|-------|----------------|
| 7:00 AM | `DoseEventScheduler` | Detects Grandma's 8 AM sugar medicine via CRON | `@Scheduled(cron="0 0 * * * *")` → `medication_schedules` |
| 7:01 AM | System | Pre-generates `DoseEvent` row (PENDING) | Idempotency check before insert |
| 7:05 AM | `OutboxPublisher` | Polls `outbox_events`, publishes to RabbitMQ | `@Scheduled(fixedDelay=5000)` → `carecircle.events` exchange |
| 8:15 AM | Ramesh | Logs into app | Google OAuth2 → JWT in HttpOnly Cookie |
| 8:16 AM | Ramesh | Sees "Pending Medication" dashboard | `GET /patients/{id}/doses` (paginated) |
| 8:20 AM | Ramesh | Marks medicine as "TAKEN" | `PUT /doses/{id}/mark` → Optimistic Locking + Outbox write |
| 8:21 AM | Anjali | Dashboard syncs *(Sprint 6)* | WebSocket STOMP *(planned)* |

---

## 🛠 Tech Stack

### Backend (Implemented)
| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Framework | Spring Boot | 4.0.3 | REST API, DI, auto-configuration |
| Database | PostgreSQL | 16 | Primary data store, JSONB, migrations |
| Cache | Redis | 7 | Session store, daily schedule cache (configured) |
| Message Queue | RabbitMQ | 3 | Async events, outbox relay |
| Scheduler | Spring `@Scheduled` + custom CRON parser | — | Pre-generate DoseEvents 24h ahead |
| ORM | JPA / Hibernate | — | Entities, optimistic locking, JSONB |
| Mapping | MapStruct | 1.6.3 | DTO ↔ Entity, compile-time mapping |
| Migrations | Flyway | — | Versioned schema evolution |
| Auth | Spring Security + OAuth2 Google | — | Google login, JWT, RBAC |
| JWT | JJWT | 0.12.6 | Token generation & validation |
| Validation | JSR-303 (Jakarta Validation) | — | `@Valid`, `@NotBlank`, custom validators |
| Monitoring | Spring Actuator | — | `/actuator/health`, metrics, prometheus |

### Frontend (Planned — Sprint 7)
| Technology | Purpose |
|-----------|---------|
| Next.js (App Router) | SSR, file-based routing |
| HttpOnly Cookie auth | XSS-proof JWT storage |
| WebSocket (STOMP) | Live dashboard sync |
| Tailwind CSS | Utility-first UI |

### DevOps (Planned — Sprint 7)
| Tool | Purpose |
|------|---------|
| Docker + docker-compose | One-command stack spin-up |
| Prometheus + Grafana | CPU, memory, latency dashboards |
| GitHub Actions | CI/CD on push to main |
| Railway / Render | Live portfolio deployment |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│               Next.js Frontend  [Sprint 7]                   │
│         (HttpOnly Cookie Auth · WebSocket · Tailwind)        │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS
┌───────────────────────────▼─────────────────────────────────┐
│                Spring Boot 4.0.3 (port 8080)                 │
│    SecurityFilterChain → JwtAuthFilter → Controllers         │
│    Google OAuth2 → JWT (HttpOnly Cookie) → RBAC              │
└──┬──────────┬──────────┬──────────────────────┬─────────────┘
   │          │          │                      │
Patient    Org      Medication              Outbox
Module    Module    Module                  Module
   │          │    (Schedule+DoseEvent)        │
   └──────────┴──────────┬─────────────────────┘
                         │
              ┌──────────▼──────────┐
              │     PostgreSQL 16    │
              │  (10 tables, Flyway) │
              └──────────┬──────────┘
                         │
         ┌───────────────┼────────────────────┐
         │               │                    │
   ┌─────▼─────┐  ┌──────▼──────┐  ┌──────────▼──────────┐
   │   Redis   │  │  RabbitMQ   │  │  DoseEventScheduler  │
   │  (Cache)  │  │  (Events)   │  │  OutboxPublisher     │
   └───────────┘  └─────────────┘  │  (@Scheduled)        │
                                   └─────────────────────-┘
```

### Transactional Outbox Pattern (Implemented ✅)
```
PUT /doses/{id}/mark  →  MedicationService.markDose()
    │
    ▼
@Transactional:
    UPDATE dose_events SET status='TAKEN', version=version+1
    INSERT INTO outbox_events (payload, status='PENDING')  ← same transaction
    COMMIT (both writes atomic)
    │
    ▼
OutboxPublisher  (@Scheduled fixedDelay=5000ms)
    SELECT * FROM outbox_events WHERE status='PENDING'
    rabbitTemplate.convertAndSend("carecircle.events", "dose.event", message)
    UPDATE outbox_events SET status='PROCESSED'
    (on failure: retry_count++, after 3 failures → status='FAILED')
```

---

## 📁 Project Structure (Actual)

```
carecircle-backend/
├── src/main/java/com/carecircle/
│   │
│   ├── CarecircleBackendApplication.java       # @SpringBootApplication + @EnableScheduling
│   │
│   ├── config/
│   │   ├── JpaConfig.java                      # @EnableJpaAuditing, ObjectMapper + JavaTimeModule
│   │   ├── JwtProperties.java                  # @ConfigurationProperties(prefix="app.jwt")
│   │   ├── RabbitMQConfig.java                 # TopicExchange, Queue (with DLX), Binding
│   │   └── SecurityConfig.java                 # SecurityFilterChain, STATELESS, CSRF off, CORS
│   │
│   ├── domain/
│   │   ├── medication/
│   │   │   ├── MedicationSchedule.java         # Entity: CRON, timezone, start/end date
│   │   │   ├── MedicationScheduleRepository.java # findAllCurrentlyActive()
│   │   │   ├── DoseEvent.java                  # Entity: @Version optimistic lock, DoseStatus enum
│   │   │   ├── DoseEventRepository.java        # findPendingDosesInWindow(), existsByScheduleIdAndScheduledAt()
│   │   │   ├── MedicationService.java          # createSchedule(), markDose() [Outbox written here]
│   │   │   ├── MedicationController.java       # @PreAuthorize("hasRole('ADMIN')") on create
│   │   │   └── dto/
│   │   │       ├── MedicationDto.java          # CreateRequest (@FutureOrPresent), Response
│   │   │       └── DoseEventDto.java           # MarkRequest, Response (includes version field)
│   │   │
│   │   ├── organization/
│   │   │   ├── Organization.java               # Entity: PlanType enum (FREE/PREMIUM/ENTERPRISE)
│   │   │   ├── OrganizationRepository.java
│   │   │   ├── OrganizationService.java
│   │   │   ├── OrganizationController.java     # POST /organizations, GET /{id}
│   │   │   └── dto/OrganizationDto.java
│   │   │
│   │   ├── outbox/
│   │   │   ├── OutboxEvent.java                # Entity: JSONB payload, OutboxStatus, retryCount, lastError
│   │   │   └── OutboxEventRepository.java      # findByStatusOrderByCreatedAtAsc()
│   │   │
│   │   ├── patient/
│   │   │   ├── Patient.java                    # Entity: JSONB metadata, soft-delete (isActive)
│   │   │   ├── PatientRepository.java          # findByIdAndOrganizationId() [tenant isolation]
│   │   │   ├── PatientService.java             # Full CRUD + soft delete
│   │   │   ├── PatientController.java          # Full CRUD, nested under /organizations/{orgId}
│   │   │   ├── dto/PatientDto.java             # CreateRequest, UpdateRequest, Response, Summary
│   │   │   └── mapper/PatientMapper.java       # MapStruct: toEntity, toResponse, toSummary, updateEntity
│   │   │
│   │   └── user/
│   │       ├── User.java                       # Entity: Role enum (ADMIN/CAREGIVER/VIEWER)
│   │       ├── UserRepository.java             # findByGoogleSubjectId(), findByEmail()
│   │       ├── RefreshToken.java               # tokenHash (SHA-256), isRevoked, isExpired(), isValid()
│   │       └── RefreshTokenRepository.java     # revokeAllByUserId() [@Modifying UPDATE]
│   │
│   ├── scheduler/
│   │   ├── DoseEventScheduler.java             # @Scheduled hourly + on startup, custom CRON parser
│   │   └── OutboxPublisher.java                # @Scheduled every 5s, 3-retry logic, FAILED status
│   │
│   ├── security/
│   │   ├── CareCircleOAuth2User.java           # OAuth2User wrapper, ROLE_ authority
│   │   ├── CareCircleOAuth2UserService.java    # Find-or-create user on Google login + auto-create org
│   │   ├── JwtAuthFilter.java                  # OncePerRequestFilter: reads access_token cookie
│   │   ├── JwtService.java                     # JJWT 0.12.6: generate access/refresh, validate, extract
│   │   └── OAuth2SuccessHandler.java           # Issues JWT cookies, stores refresh token hash (SHA-256)
│   │
│   └── shared/
│       ├── BaseEntity.java                     # @MappedSuperclass: UUID id, createdAt, updatedAt
│       └── exception/
│           ├── GlobalExceptionHandler.java     # @RestControllerAdvice: 400, 404, 500
│           └── ResourceNotFoundException.java
│
├── src/main/resources/
│   ├── application.yaml                        # Fully commented config (Hikari, JPA, Redis, RabbitMQ, OAuth2)
│   └── db/migration/
│       └── V1__init_schema.sql                 # 10 tables with indexes, constraints, comments
│
└── pom.xml                                     # Spring Boot 4.0.3, JJWT 0.12.6, MapStruct 1.6.3
```

---

## 🗄 Database Schema

**10 tables** defined in `V1__init_schema.sql` (with inline `COMMENT` documentation):

| Table | Module | Key Design Decisions |
|-------|--------|---------------------|
| `organizations` | Identity | Multi-tenant anchor. `plan_type` CHECK (FREE/PREMIUM/ENTERPRISE) |
| `users` | Identity | `google_subject_id` UNIQUE, `role` CHECK (ADMIN/CAREGIVER/VIEWER), unique email per org |
| `refresh_tokens` | Identity | `token_hash` stores SHA-256 (never raw token), `is_revoked`, index on `user_id` |
| `patients` | Care | `metadata JSONB DEFAULT '{}'`, soft-delete via `is_active`, index on `organization_id` |
| `caregiver_assignments` | Care | Junction table — UNIQUE(caregiver_id, patient_id), `assigned_by` FK |
| `medication_schedules` | Medication | `cron_expression` VARCHAR, `timezone` VARCHAR, `start_date/end_date`, two partial indexes |
| `dose_events` | Medication | `version INTEGER` (optimistic lock), UNIQUE(schedule_id, scheduled_at), two partial indexes |
| `vital_readings` | Health | `reading_value JSONB`, `is_anomalous`, `alert_triggered`, type CHECK constraint |
| `outbox_events` | Messaging | `payload JSONB`, `retry_count`, `last_error TEXT`, partial index on PENDING |
| `audit_logs` | Audit | **No FK constraints** (log outlives data), `ip_address INET`, append-only by convention |

---

## 🔌 API Endpoints (Implemented)

### Organization
```
POST   /api/v1/organizations              → Create org
GET    /api/v1/organizations/{id}         → Get org by ID
```

### Patient (tenant-scoped)
```
POST   /api/v1/organizations/{orgId}/patients                         → Create patient
GET    /api/v1/organizations/{orgId}/patients                         → List (paginated, sortable)
GET    /api/v1/organizations/{orgId}/patients/{patientId}             → Get one
PUT    /api/v1/organizations/{orgId}/patients/{patientId}             → Partial update
DELETE /api/v1/organizations/{orgId}/patients/{patientId}             → Soft deactivate (204)
```

### Medication (RBAC enforced)
```
POST   /api/v1/organizations/{orgId}/medications                                  → Create schedule [ADMIN only]
GET    /api/v1/organizations/{orgId}/patients/{patientId}/medications             → List active schedules
GET    /api/v1/organizations/{orgId}/patients/{patientId}/doses                  → List dose events (paginated)
PUT    /api/v1/organizations/{orgId}/doses/{doseEventId}/mark                    → Mark TAKEN or SKIPPED
```

### Auth (OAuth2 — Spring managed)
```
GET    /login/oauth2/code/google          → Google OAuth2 callback
       Response: Sets access_token (HttpOnly, path=/) + refresh_token (HttpOnly, path=/api/v1/auth/refresh)
       Redirects to: http://localhost:3000/dashboard
```

### Health & Observability
```
GET    /actuator/health                   → {"status":"UP", components: db, redis, rabbit}
GET    /actuator/metrics                  → Micrometer metrics
GET    /actuator/prometheus               → Prometheus scrape endpoint
```

---

## ✅ Progress Tracker

```
Sprint 1 — Foundation        [██████████] 100%  ✅ COMPLETE
Sprint 2 — Core APIs         [██████████] 100%  ✅ COMPLETE
Sprint 3 — Security          [██████████] 100%  ✅ COMPLETE
Sprint 4 — Medication Engine [██████████] 100%  ✅ COMPLETE
Sprint 5 — Performance       [░░░░░░░░░░]   0%  🔄 IN PROGRESS  ← current
Sprint 6 — Product Features  [░░░░░░░░░░]   0%  ⏳ upcoming
Sprint 7 — Production        [░░░░░░░░░░]   0%  ⏳ upcoming

Overall: ██████████████████░░  ~57%  (4/7 sprints done)
```

---

## ✅ Sprint 1 — Foundation & Infrastructure (Week 1) — COMPLETE

| Task | Status | What Was Built |
|------|--------|----------------|
| PostgreSQL Schema | ✅ | `V1__init_schema.sql` — 10 tables, UUID PKs, CHECK constraints, partial indexes, inline SQL COMMENTs |
| Spring Boot 4 Setup | ✅ | `application.yaml` — Hikari pool, JPA validate, Redis lettuce pool, RabbitMQ publisher-confirm |
| Docker Infrastructure | ✅ | PostgreSQL 16, Redis 7, RabbitMQ 3-management |
| JPA Entities | ✅ | `BaseEntity` (`@MappedSuperclass`) + 7 domain entities with `@EntityListeners(AuditingEntityListener.class)` |
| Actuator Health Check | ✅ | All 5 services UP — DB, Redis, RabbitMQ, Disk, JVM — exposed at `/actuator/health` |

**Key config decisions (in `application.yaml`):**
- `ddl-auto: validate` — Flyway owns schema, Hibernate only validates it
- `publisher-confirm-type: correlated` + `acknowledge-mode: manual` — reliable RabbitMQ delivery
- `preferred_uuid_jdbc_type: VARCHAR` — consistent UUID handling across Hibernate + PG
- JDBC batch size 50, `order_inserts/updates: true` — bulk write performance
- Redis `timeout: 2000ms` — fail fast on cache miss, don't hang app

---

## ✅ Sprint 2 — Core APIs (Week 2) — COMPLETE

| Task | Status | What Was Built |
|------|--------|----------------|
| Patient API (full CRUD) | ✅ | `PatientController` → `PatientService` → `PatientRepository` |
| Organization API | ✅ | `OrganizationController` → `OrganizationService` |
| Global Exception Handler | ✅ | `@RestControllerAdvice` — typed 400/404/500 JSON responses |
| Input Validation | ✅ | `@NotBlank`, `@Past` (DOB), `@Pattern` (blood type regex), `@Size`, `@FutureOrPresent` |
| Pagination & Sorting | ✅ | `Pageable`, `Page<T>`, `@PageableDefault(size=20, sort="fullName")` |
| MapStruct Integration | ✅ | `PatientMapper`: `toEntity`, `toResponse`, `toSummary`, `updateEntity` (`@MappingTarget`) |

**Key design decisions:**
- 4 separate DTOs per domain (`CreateRequest`, `UpdateRequest`, `Response`, `Summary`) — never expose entities
- `findByIdAndOrganizationId()` — multi-tenant isolation enforced at the query level, not application level
- Soft delete (`is_active = false`) — medical records never hard-deleted
- `@Transactional(readOnly=true)` on all GET methods — Hibernate skips dirty checking
- `NullValuePropertyMappingStrategy.IGNORE` in MapStruct — safe partial updates

---

## ✅ Sprint 3 — Security (Week 3) — COMPLETE

| Task | Status | What Was Built |
|------|--------|----------------|
| Google OAuth2 Flow | ✅ | `CareCircleOAuth2UserService` — find-or-create user, auto-create org for new users (ADMIN role) |
| JWT in HttpOnly Cookies | ✅ | `OAuth2SuccessHandler` — `access_token` (path=/) + `refresh_token` (path=/api/v1/auth/refresh) |
| Refresh Token Rotation | ✅ | `RefreshToken` entity, `tokenHash` (SHA-256 via `MessageDigest`), `revokeAllByUserId()` on login |
| RBAC | ✅ | `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` on `createSchedule()` |
| `SecurityFilterChain` | ✅ | STATELESS session, CSRF disabled, CORS for `localhost:3000`, `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter` |

**Security flow (implemented):**
```
User → /oauth2/authorization/google
     → Google consent
     → /login/oauth2/code/google  (Spring handles token exchange)
     → CareCircleOAuth2UserService.loadUser()  (find or create User + Org)
     → OAuth2SuccessHandler.onAuthenticationSuccess()
         → generateAccessToken(userId, email, role, orgId)  [15min]
         → generateRefreshToken(userId)  [7 days]
         → SHA-256 hash refreshToken → store in refresh_tokens table
         → revokeAllByUserId() — invalidate previous sessions
         → Set HttpOnly cookies
         → Redirect to localhost:3000/dashboard
```

**On every subsequent request:**
```
JwtAuthFilter reads access_token cookie
    → jwtService.isTokenValid(token)
    → extractUserId() → userRepository.findById()
    → SecurityContextHolder.setAuthentication(...)
    → @PreAuthorize and @AuthenticationPrincipal work downstream
```

**⚠️ The one missing piece (Sprint 3/4 boundary):**
- `POST /api/v1/auth/refresh` endpoint — controller + service for refresh token rotation not yet built

---

## ✅ Sprint 4 — Medication Engine (Week 4) — COMPLETE

| Task | Status | What Was Built |
|------|--------|----------------|
| Medication Schedule API | ✅ | `createSchedule()` — CRON expression stored, timezone-aware, `@FutureOrPresent` on startDate |
| Dose Pre-generation Scheduler | ✅ | `DoseEventScheduler` — runs hourly via `@Scheduled(cron="0 0 * * * *")`, also on startup (10s delay) |
| Custom CRON Parser | ✅ | Handles `"0 8 * * *"` (daily) and `"0 8,20 * * *"` (multiple times) in `parseCronAndGetFiringTimes()` |
| Idempotency Guard | ✅ | `existsByScheduleIdAndScheduledAt()` — no duplicate dose events on scheduler overlap/restart |
| Transactional Outbox | ✅ | `OutboxEvent.of(...)` written in `markDose()` in the **same `@Transactional` as the dose update** |
| RabbitMQ Publisher | ✅ | `OutboxPublisher` — polls every 5s, 3-retry limit, `lastError` stored, marks FAILED after max retries |
| Dose Status API | ✅ | `markDose()` — validates PENDING→TAKEN/SKIPPED, `@Version` catches concurrent updates → 409 |
| Dead Letter Queue | ✅ | `QueueBuilder.durable(...).withArgument("x-dead-letter-exchange", "carecircle.dlx")` |
| **Auth Refresh Rotation** | ✅ | `AuthService.refresh()` + `AuthController` — `/auth/refresh`, `/auth/logout`, `/auth/me` |

**The `markDose()` method — the crown jewel of Sprint 4:**
```java
@Transactional
public DoseEventDto.Response markDose(UUID doseEventId, MarkRequest request, User actionedBy) {
    // 1. Validate: dose must be PENDING, status must be TAKEN or SKIPPED
    // 2. Update: dose.status, actionedBy, actionedAt, notes
    // 3. Save: Hibernate increments @Version automatically
    //    → If concurrent update: ObjectOptimisticLockingFailureException → caught → 409
    // 4. Write OutboxEvent in same @Transactional:
    //    OutboxEvent.of("DoseEvent", id, "DOSE_TAKEN", Map.of(doseId, patientId, ...))
    //    → If RabbitMQ is down later, event stays in outbox until relayed
}
```

**Auth endpoints added (`com/carecircle/security/`):**

| File | What it does |
|------|-------------|
| `AuthService.java` | `refresh()` — hash → validate `isValid()` → `revokeAllByUserId()` → issue new pair → set cookies |
| `AuthService.java` | `logout()` — revoke all tokens + clear both cookies with `MaxAge=0` |
| `AuthController.java` | `POST /api/v1/auth/refresh` — `@CookieValue(required=false)` + delegates to `AuthService` |
| `AuthController.java` | `POST /api/v1/auth/logout` — revoke + clear cookies |
| `AuthController.java` | `GET /api/v1/auth/me` — returns current user from `@AuthenticationPrincipal` |

**Replay attack protection built in:** If an attacker steals a refresh token and uses it first, the legitimate user's next `/auth/refresh` call finds the token already revoked → returns 401. The attack is detectable.

**Note:** Quartz v2.5.2 is running (visible in startup logs). Currently using Spring `@Scheduled` for dose generation — full Quartz `CronExpression` integration is a future improvement for `L`, `W`, `#` CRON fields.

---

## 🔄 Sprint 5 — Performance (Week 5) — IN PROGRESS

**Goal:** Make the app fast under production load and resilient to abuse.

| Task | Signal |
|------|--------|
| Redis `@Cacheable` | Cache `getDoseEvents()` — key: `daily_schedule:{orgId}:{patientId}:{date}` |
| `@CacheEvict` | Invalidate on `markDose()` and `createSchedule()` for that patient |
| Rate Limiting (Bucket4j) | 100 req/min per user, token bucket algorithm — block abuse |
| N+1 Fix (`@EntityGraph`) | `DoseEvent` → `schedule` + `patient` in one JOIN — audit with `EXPLAIN ANALYZE` |
| Async Vital Alerts | BP > 160 → `OutboxEvent` type `"BP_ANOMALY_DETECTED"` → RabbitMQ → push |

**Redis already configured in `application.yaml`:**
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 300000   # 5 minutes
      cache-null-values: false
```

**Cache key design:**
```
daily_schedule:{orgId}:{patientId}:{date}  →  Page<DoseEventDto.Response>
Evict trigger: any write to dose_events for that patientId
```

**Bucket4j setup (planned):**
```java
// 100 requests per minute per authenticated user
Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
Bucket bucket = Bucket.builder().addLimit(limit).build();
```

---

## ⏳ Sprint 6 — Product Features (Week 6) — UPCOMING

**Goal:** Features that make this feel like a real product, not just CRUD.

| Task | Signal |
|------|--------|
| WebSocket (STOMP) | `/ws` endpoint → `/topic/org/{orgId}/dashboard` — Anjali's screen updates live |
| OpenPDF Report | `GET /patients/{id}/report?month=2026-03` — monthly health PDF for doctor |
| Audit Log API | `GET /patients/{id}/audit` — reads from `audit_logs` (schema already in V1) |
| Health Vitals API | `POST /patients/{id}/vitals` with JSONB `reading_value`, anomaly flag |
| Swagger / OpenAPI 3 | Full docs at `/swagger-ui.html` via SpringDoc |

**WebSocket event flow (planned):**
```
markDose()
  → OutboxPublisher → RabbitMQ
  → WebSocket consumer receives event
  → SimpMessagingTemplate.convertAndSend("/topic/org/{orgId}/dashboard", doseUpdate)
  → Anjali's Next.js app re-renders dashboard without refresh
```

**Vitals schema already in DB (ready to implement):**
```sql
vital_readings(patient_id, vital_type, reading_value JSONB, is_anomalous, alert_triggered)
-- Examples:
-- {"systolic": 130, "diastolic": 85}   → BLOOD_PRESSURE
-- {"value": 110, "unit": "mg/dL"}       → BLOOD_SUGAR
```

---

## ⏳ Sprint 7 — Production & Frontend (Week 7–8) — UPCOMING

**Goal:** One-command local. Monitored. Deployed. On resume.

| Task | Signal |
|------|--------|
| `docker-compose.yml` | Single command: PG 16 + Redis 7 + RabbitMQ 3 + Spring app |
| Prometheus + Grafana | Scrape `/actuator/prometheus`, CPU/memory/request-latency dashboards |
| Next.js Frontend | HttpOnly cookie auth, real-time dashboard via WebSocket |
| GitHub Actions CI/CD | `./mvnw test` on PR + deploy on push to `main` |
| Railway / Render | Live URL for portfolio |

**Next.js auth pattern (planned — never localStorage):**
```typescript
// credentials: 'include' sends the HttpOnly cookie automatically
export async function getCurrentUser(): Promise<User | null> {
  const res = await fetch(`${API_URL}/api/v1/users/me`, { credentials: 'include' });
  if (!res.ok) return null;
  return res.json();
}
```

---

## 🧠 Key Engineering Decisions (Hiring Signals)

| Concern | Junior Approach | CareCircle Approach |
|---------|----------------|---------------------|
| Auth | Username + password in body | Google OAuth2 + JWT in `HttpOnly; Secure` Cookie |
| Token Storage | `localStorage` (XSS-vulnerable) | HttpOnly Cookie — JS can **never** read it |
| Concurrent Updates | Last-write-wins | `@Version` Optimistic Locking → `ObjectOptimisticLockingFailureException` → 409 |
| Notification Reliability | Direct `rabbitTemplate.send()` — can lose on crash | **Transactional Outbox** — atomic DB write + background relay |
| Refresh Token Security | Store raw token | Store **SHA-256 hash**, revoke-all on every new login |
| Scheduling | Hardcoded `Thread.sleep` | `@Scheduled` CRON → **materialise** `dose_events` rows — simple indexed query at runtime |
| Duplicate Prevention | Nothing | `existsByScheduleIdAndScheduledAt()` idempotency check before every insert |
| Performance | Load all rows | `Pageable`, `Page<T>`, `@Transactional(readOnly=true)` everywhere |
| Data Flexibility | 10 nullable columns | `metadata JSONB`, `reading_value JSONB` — queryable, indexable |
| Audit Trail | None | `audit_logs` — append-only, **no FK constraints** (log outlives deleted data) |
| Multi-tenancy | No isolation | `findByIdAndOrganizationId()` — tenant boundary enforced at **query level** |
| Error Handling | Random 500 HTML pages | `@RestControllerAdvice` — typed JSON errors with field-level validation messages |
| Schema Changes | `ddl-auto: update` (dangerous) | `ddl-auto: validate` + **Flyway migrations** |
| DTO Conversion | Manual getters/setters | **MapStruct** compile-time code gen — as fast as hand-written, no reflection |

---

## 🚀 Getting Started

### Prerequisites
- Java 21+
- Docker Desktop
- Maven 3.9+

### 1. Start Infrastructure

```bash
# PostgreSQL 16
docker run --name carecircle-postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=carecircle_db \
  -p 5432:5432 -d postgres:16

# Redis 7
docker run --name carecircle-redis -p 6379:6379 -d redis:7

# RabbitMQ 3 (management UI at http://localhost:15672 — guest/guest)
docker run --name carecircle-rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  -d rabbitmq:3-management
```

### 2. Set Environment Variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/carecircle_db
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export GOOGLE_CLIENT_ID=your_google_client_id
export GOOGLE_CLIENT_SECRET=your_google_client_secret
export JWT_SECRET=your-secret-at-least-32-characters-long
export JWT_EXPIRY_MS=900000          # 15 minutes
export JWT_REFRESH_EXPIRY_MS=604800000  # 7 days
```

### 3. Run

```bash
git clone https://github.com/dev-yash05/carecircle-backend.git
cd carecircle-backend
./mvnw spring-boot:run
```

### 4. Verify Everything is Up

```bash
curl http://localhost:8080/actuator/health | jq
# Expected:
# {
#   "status": "UP",
#   "components": {
#     "db": { "status": "UP" },
#     "redis": { "status": "UP" },
#     "rabbit": { "status": "UP" }
#   }
# }
```

Flyway will auto-run `V1__init_schema.sql` on startup — all 10 tables created automatically.

---

## 🔐 Environment Variables

| Variable | Required | Example | Notes |
|----------|----------|---------|-------|
| `DB_URL` | ✅ | `jdbc:postgresql://localhost:5432/carecircle_db` | |
| `DB_USERNAME` | ✅ | `postgres` | |
| `DB_PASSWORD` | ✅ | `postgres` | |
| `GOOGLE_CLIENT_ID` | ✅ | `123...apps.googleusercontent.com` | Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | ✅ | `GOCSPX-...` | Google Cloud Console |
| `JWT_SECRET` | ✅ | 32+ char random string | HMAC-SHA256 signing key |
| `JWT_EXPIRY_MS` | ✅ | `900000` | 15 min recommended |
| `JWT_REFRESH_EXPIRY_MS` | ✅ | `604800000` | 7 days recommended |

**Google Cloud Console setup:**
1. [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials
2. Create OAuth 2.0 Client ID (Web application)
3. Authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`

---

## 💬 Continuing in a New Chat

Paste this README link + one of these prompts into a new Claude session:

### ▶ Complete Sprint 4 — the missing 5% (`/auth/refresh` endpoint):
```
Here is my CareCircle backend README: [GitHub raw link]

Sprints 1–3 are complete and Sprint 4 is 95% done.
The one missing piece is the POST /api/v1/auth/refresh endpoint.

Existing code to use:
- RefreshToken entity (fields: tokenHash, expiresAt, isRevoked, isValid(), isExpired())
- RefreshTokenRepository (findByTokenHash(), revokeAllByUserId())
- JwtService (generateAccessToken(), generateRefreshToken(), extractUserId())
- JwtProperties (expiryMs, refreshExpiryMs)
- OAuth2SuccessHandler shows exactly how HttpOnly cookies are currently set

Build:
1. AuthService.refresh(String refreshTokenCookie):
   - Hash the incoming cookie value (SHA-256)
   - findByTokenHash() → validate isValid()
   - revokeAllByUserId(user.id)
   - Generate new access + refresh tokens
   - Save new RefreshToken (hashed) to DB
   - Return new tokens
2. AuthController: POST /api/v1/auth/refresh
   - Read refresh_token from @CookieValue
   - Call AuthService.refresh()
   - Set new HttpOnly cookies in HttpServletResponse
   - Return 200 OK
```

### ▶ Start Sprint 5 — Redis Caching + Rate Limiting:
```
Here is my CareCircle backend README: [GitHub raw link]

Sprints 1–4 are complete. Start Sprint 5 — Performance.

Build:
1. @Cacheable on MedicationService.getDoseEvents()
   Cache key: "daily_schedule:{orgId}:{patientId}:{date}"
   Redis config already in application.yaml: cache.type=redis, TTL 300000ms

2. @CacheEvict on markDose() and createSchedule() for the patient's cache key

3. Bucket4j rate limiting filter: 100 req/min per authenticated userId
   Apply as OncePerRequestFilter, return 429 Too Many Requests on breach

4. Fix N+1 in MedicationService: add @EntityGraph to DoseEventRepository
   to eager-fetch schedule + patient in one query. Run EXPLAIN ANALYZE.

5. Async Vital Alerts stub: when BP systolic > 160 in new VitalsService,
   write OutboxEvent type "BP_ANOMALY_DETECTED" (same pattern as DOSE_TAKEN)
```

### ▶ Start Sprint 6 — Product Features:
```
Here is my CareCircle backend README: [GitHub raw link]

Sprints 1–5 are complete. Start Sprint 6 — Product Features.

Build:
1. WebSocket (STOMP): configure WebSocketMessageBrokerConfigurer,
   /ws endpoint, /topic/org/{orgId}/dashboard destination.
   After markDose() commits, publish update to this topic.

2. Health Vitals API: POST /organizations/{orgId}/patients/{patientId}/vitals
   Body: { vitalType, readingValue: JSONB }
   Anomaly detection: if BLOOD_PRESSURE systolic > 160 → is_anomalous=true + OutboxEvent

3. Audit Log API: GET /organizations/{orgId}/patients/{patientId}/audit
   audit_logs table already exists in V1 migration.
   Paginated, sorted by created_at DESC.

4. OpenPDF: GET /organizations/{orgId}/patients/{patientId}/report?month=2026-03
   Aggregate dose_events + vital_readings for the month, stream PDF.

5. SpringDoc OpenAPI 3: add dependency, @OpenAPIDefinition, @Operation on controllers.
   Accessible at /swagger-ui.html.
```

---

## 📊 Sprint Velocity Reference

| Sprint | Focus | Duration | Status |
|--------|-------|----------|--------|
| 1 | Foundation | Week 1 | ✅ Complete |
| 2 | Core APIs | Week 2 | ✅ Complete |
| 3 | Security (OAuth2 + JWT) | Week 3 | ✅ Complete |
| 4 | Medication Engine | Week 4 | 🔄 95% — `/auth/refresh` missing |
| 5 | Performance (Cache + Rate Limit) | Week 5 | ⏳ Not started |
| 6 | Product Features (WS + PDF + Vitals) | Week 6 | ⏳ Not started |
| 7 | Production + Next.js Frontend | Week 7–8 | ⏳ Not started |

---

*SENIOR HIRING SIGNAL PROJECT · 8 WEEKS · PRODUCTION GRADE*  
*Last updated: Sprint 4 ✅ COMPLETE — Auth Refresh Rotation, Medication Engine, Outbox Pattern all done*  
*Repo: https://github.com/dev-yash05/carecircle-backend*  
*Next step: Start Sprint 5 — Redis `@Cacheable` + Bucket4j Rate Limiting*