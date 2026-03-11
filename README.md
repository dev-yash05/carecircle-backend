# 🏥 CareCircle Backend

> **Production-grade family caregiving platform — fully built and running.**
> Spring Boot 4 · PostgreSQL (Neon) · Redis · RabbitMQ · Docker · Java 21

[![Progress](https://img.shields.io/badge/Progress-Sprint%207%20Complete-brightgreen)](https://github.com/dev-yash05/carecircle-backend)
[![Stack](https://img.shields.io/badge/Stack-Spring%20Boot%204%20%7C%20PostgreSQL%20%7C%20Redis%20%7C%20RabbitMQ-blue)](https://github.com/dev-yash05/carecircle-backend)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://github.com/dev-yash05/carecircle-backend)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)](https://github.com/dev-yash05/carecircle-backend)

---

## 📖 Table of Contents

- [Project Overview](#-project-overview)
- [The Problem We Solve](#-the-problem-we-solve)
- [Real-World Use Case](#-real-world-use-case-a-morning-with-grandma)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Project Structure](#-project-structure)
- [Database Schema](#-database-schema)
- [API Endpoints](#-api-endpoints)
- [Progress Tracker](#-progress-tracker)
- [Key Engineering Decisions](#-key-engineering-decisions-hiring-signals)
- [Running Locally](#-running-locally)
- [Environment Variables](#-environment-variables)

---

## 🎯 Project Overview

**CareCircle** is a production-grade remote caregiving coordination platform. It enables family members to monitor and coordinate care for elderly relatives — tracking medication schedules, health vitals, and caregiver activity in real time, across devices.

Every architectural decision is chosen to demonstrate **senior-level engineering judgment**: distributed systems patterns, data integrity guarantees, security best practices, and observable production infrastructure.

| Metric | Value |
|--------|-------|
| Total Sprints | 7 |
| **Current Status** | **All 7 Sprints Complete ✅** |
| Overall Progress | 100% — running in Docker |
| Spring Boot | 4.0.3 |
| Java | 21 |
| Database | PostgreSQL 17 (Neon cloud) |

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
| 8:16 AM | Ramesh | Sees "Pending Medication" dashboard | `GET /patients/{id}/doses` (paginated, cached) |
| 8:20 AM | Ramesh | Marks medicine as "TAKEN" | `PUT /doses/{id}/mark` → Optimistic Locking + Outbox write |
| 8:21 AM | Anjali | Dashboard syncs live ✅ | WebSocket STOMP → `/topic/org/{orgId}/dashboard` |
| 9:00 AM | Ramesh | Records BP reading | `POST /vitals` → anomaly detection → `BP_ANOMALY_DETECTED` outbox event |

---

## 🛠 Tech Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Framework | Spring Boot | 4.0.3 | REST API, DI, auto-configuration |
| Database | PostgreSQL | 17 (Neon) | Primary data store, JSONB, migrations |
| Cache | Redis | 7 | `@Cacheable` medication schedules, Bucket4j rate limiting |
| Message Queue | RabbitMQ | 3 | Async events, outbox relay, dead-letter queue |
| WebSocket | STOMP over SockJS | — | Real-time dashboard updates |
| Scheduler | Spring `@Scheduled` | — | Pre-generate DoseEvents 24h ahead |
| ORM | JPA / Hibernate | — | Entities, optimistic locking, JSONB |
| Mapping | MapStruct | 1.6.3 | DTO ↔ Entity, compile-time mapping |
| Migrations | Flyway | — | Versioned schema evolution |
| Auth | Spring Security + OAuth2 Google | — | Google login, JWT, RBAC |
| JWT | JJWT | 0.12.6 | Token generation & validation |
| PDF | OpenPDF | 1.3.35 | Monthly patient health reports |
| API Docs | SpringDoc OpenAPI 3 | 2.8.3 | Swagger UI at `/swagger-ui.html` |
| Rate Limiting | Bucket4j | 8.10.1 | 100 req/min per user, Redis-backed |
| Container | Docker + Compose | — | One-command local stack |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│               Next.js Frontend  [planned]                    │
│         (HttpOnly Cookie Auth · WebSocket · Tailwind)        │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS
┌───────────────────────────▼─────────────────────────────────┐
│                Spring Boot 4.0.3 (port 8080)                 │
│  RateLimitFilter → SecurityFilterChain → JwtAuthFilter        │
│  Google OAuth2 → JWT (HttpOnly Cookie) → RBAC                │
│  WebSocket STOMP → /topic/org/{orgId}/dashboard              │
└──┬──────────┬──────────┬──────────┬──────────┬──────────────┘
   │          │          │          │          │
Patient    Org      Medication   Vital      Audit
Module    Module    Module       Module     Module
   │          │    (Schedule+       │          │
   └──────────┴── DoseEvent) ───────┴──────────┘
                         │
              ┌──────────▼──────────┐
              │  PostgreSQL 17 Neon  │
              │  (10 tables, Flyway) │
              └──────────┬──────────┘
                         │
         ┌───────────────┼─────────────────────┐
         │               │                     │
   ┌─────▼─────┐  ┌──────▼──────┐  ┌───────────▼──────────┐
   │   Redis   │  │  RabbitMQ   │  │  DoseEventScheduler   │
   │  (Cache + │  │  (Events +  │  │  OutboxPublisher      │
   │  Rate lim)│  │   DLQ)      │  │  (@Scheduled)         │
   └───────────┘  └─────────────┘  └──────────────────────-┘
```

### Transactional Outbox Pattern

```
PUT /doses/{id}/mark  →  MedicationService.markDose()
    │
    ▼
@Transactional:
    UPDATE dose_events SET status='TAKEN', version=version+1  ← optimistic lock
    INSERT INTO outbox_events (payload, status='PENDING')     ← same transaction
    messagingTemplate.convertAndSend("/topic/org/{orgId}/dashboard", ...)
    COMMIT (all three writes atomic)
    │
    ▼
OutboxPublisher  (@Scheduled fixedDelay=5000ms)
    SELECT * FROM outbox_events WHERE status='PENDING'
    rabbitTemplate.convertAndSend("carecircle.events", "dose.event", message)
    UPDATE outbox_events SET status='PROCESSED'
    (on failure: retry_count++, after 3 failures → status='FAILED' → DLQ)
```

---

## 📁 Project Structure

```
carecircle-backend/
├── Dockerfile                              # Multi-stage build (Maven → JRE Alpine)
├── docker-compose.yml                      # Redis + RabbitMQ + App (Neon is external)
├── .env.example                            # Template — copy to .env and fill in values
├── .dockerignore
│
└── src/main/java/com/carecircle/
    │
    ├── CarecircleBackendApplication.java   # @SpringBootApplication + @EnableScheduling + @EnableAsync
    │
    ├── config/
    │   ├── JpaConfig.java                  # @EnableJpaAuditing, ObjectMapper + JavaTimeModule
    │   ├── JwtProperties.java              # @ConfigurationProperties(prefix="app.jwt")
    │   ├── RabbitMQConfig.java             # TopicExchange, Queue (with DLX), Binding
    │   ├── SecurityConfig.java             # SecurityFilterChain, STATELESS, CSRF off, CORS
    │   ├── WebSocketConfig.java            # STOMP: /ws endpoint, /topic broker, /app prefix
    │   └── OpenApiConfig.java              # @OpenAPIDefinition, CookieAuth SecurityScheme
    │
    ├── domain/
    │   ├── audit/
    │   │   ├── AuditLog.java               # Entity: no FK (log outlives data), inet ip_address
    │   │   ├── AuditLogRepository.java
    │   │   ├── AuditService.java           # @Async + @Transactional(REQUIRES_NEW) — never blocks caller
    │   │   ├── AuditController.java        # GET /audit — paginated, sorted created_at DESC
    │   │   └── dto/AuditLogDto.java
    │   │
    │   ├── medication/
    │   │   ├── MedicationSchedule.java     # Entity: CRON, timezone, start/end date
    │   │   ├── DoseEvent.java              # Entity: @Version optimistic lock, DoseStatus enum
    │   │   ├── MedicationService.java      # markDose() — Outbox + WebSocket push in one @Transactional
    │   │   ├── MedicationController.java   # @PreAuthorize("hasRole('ADMIN')") on create
    │   │   └── dto/
    │   │
    │   ├── organization/
    │   │   └── ...
    │   │
    │   ├── outbox/
    │   │   ├── OutboxEvent.java            # Entity: JSONB payload, retry_count, last_error
    │   │   └── OutboxEventRepository.java
    │   │
    │   ├── patient/
    │   │   ├── Patient.java                # Entity: JSONB metadata, soft-delete
    │   │   ├── PatientService.java         # Full CRUD + soft delete
    │   │   ├── PatientController.java      # Nested under /organizations/{orgId}
    │   │   └── mapper/PatientMapper.java   # MapStruct compile-time mapping
    │   │
    │   ├── report/
    │   │   ├── PatientReportService.java   # OpenPDF — aggregates dose + vital data
    │   │   └── PatientReportController.java # GET /report?month=YYYY-MM → streams PDF
    │   │
    │   ├── user/
    │   │   ├── User.java                   # Entity: Role enum (ADMIN/CAREGIVER/VIEWER)
    │   │   ├── RefreshToken.java           # tokenHash (SHA-256), isRevoked, isValid()
    │   │   └── RefreshTokenRepository.java
    │   │
    │   └── vital/
    │       ├── VitalReading.java           # Entity: reading_value JSONB, is_anomalous
    │       ├── VitalService.java           # Anomaly detection thresholds
    │       ├── VitalController.java        # POST + GET vitals
    │       └── dto/VitalDto.java
    │
    ├── scheduler/
    │   ├── DoseEventScheduler.java         # @Scheduled hourly + on startup
    │   └── OutboxPublisher.java            # @Scheduled every 5s, 3-retry logic
    │
    ├── security/
    │   ├── CareCircleOAuth2UserService.java # Find-or-create user on Google login
    │   ├── JwtAuthFilter.java              # OncePerRequestFilter: reads access_token cookie
    │   ├── JwtService.java                 # JJWT 0.12.6: generate/validate/extract
    │   ├── OAuth2SuccessHandler.java       # Issues JWT cookies, stores refresh token hash
    │   ├── AuthService.java                # Refresh rotation, logout, token revocation
    │   └── AuthController.java             # /auth/refresh, /auth/logout, /auth/me
    │
    └── shared/
        ├── BaseEntity.java                 # UUID id, createdAt, updatedAt
        ├── RateLimitFilter.java            # Bucket4j: 100 req/min per user, @Order(1)
        └── exception/
            ├── GlobalExceptionHandler.java # 400/403/404/409/500 typed JSON responses
            └── ResourceNotFoundException.java

└── src/main/resources/
    ├── application.yaml                    # Base config (Hikari, JPA, Redis, RabbitMQ, OAuth2)
    ├── application-prod.yml                # Prod overrides: logging, pool sizes, Tomcat tuning
    └── db/migration/
        └── V1__init_schema.sql             # 10 tables with indexes, constraints, COMMENT docs
```

---

## 🗄 Database Schema

10 tables defined in `V1__init_schema.sql`:

| Table | Module | Key Design Decisions |
|-------|--------|----------------------|
| `organizations` | Identity | Multi-tenant anchor. `plan_type` CHECK (FREE/PREMIUM/ENTERPRISE) |
| `users` | Identity | `google_subject_id` UNIQUE, `role` CHECK, unique email per org |
| `refresh_tokens` | Identity | `token_hash` SHA-256 (never raw token), `is_revoked` |
| `patients` | Care | `metadata JSONB`, soft-delete via `is_active` |
| `caregiver_assignments` | Care | Junction table — UNIQUE(caregiver_id, patient_id) |
| `medication_schedules` | Medication | `cron_expression`, `timezone`, `start_date/end_date` |
| `dose_events` | Medication | `version INTEGER` (optimistic lock), UNIQUE(schedule_id, scheduled_at) |
| `vital_readings` | Health | `reading_value JSONB`, `is_anomalous`, `alert_triggered` |
| `outbox_events` | Messaging | `payload JSONB`, `retry_count`, `last_error`, partial index on PENDING |
| `audit_logs` | Audit | No FK constraints (log outlives data), `ip_address INET`, append-only |

---

## 🔌 API Endpoints

### Auth
```
GET    /login/oauth2/code/google                   → Google OAuth2 callback → sets JWT cookies
POST   /api/v1/auth/refresh                        → Rotate refresh token
POST   /api/v1/auth/logout                         → Revoke tokens + clear cookies
GET    /api/v1/auth/me                             → Current user from JWT
```

### Organization
```
POST   /api/v1/organizations                       → Create org
GET    /api/v1/organizations/{id}                  → Get org
```

### Patient
```
POST   /api/v1/organizations/{orgId}/patients                      → Create
GET    /api/v1/organizations/{orgId}/patients                      → List (paginated)
GET    /api/v1/organizations/{orgId}/patients/{patientId}          → Get one
PUT    /api/v1/organizations/{orgId}/patients/{patientId}          → Partial update
DELETE /api/v1/organizations/{orgId}/patients/{patientId}          → Soft deactivate (204)
```

### Medication
```
POST   /api/v1/organizations/{orgId}/medications                                   → Create schedule [ADMIN only]
GET    /api/v1/organizations/{orgId}/patients/{patientId}/medications              → List active schedules [@Cacheable]
GET    /api/v1/organizations/{orgId}/patients/{patientId}/doses                   → List dose events (paginated, @EntityGraph)
PUT    /api/v1/organizations/{orgId}/doses/{doseEventId}/mark                     → Mark TAKEN/SKIPPED → WebSocket push
```

### Vitals
```
POST   /api/v1/organizations/{orgId}/patients/{patientId}/vitals   → Record reading (anomaly detection)
GET    /api/v1/organizations/{orgId}/patients/{patientId}/vitals   → List readings (?vitalType=BLOOD_PRESSURE)
```

### Audit
```
GET    /api/v1/organizations/{orgId}/patients/{patientId}/audit    → Audit log (paginated, sorted desc)
```

### Report
```
GET    /api/v1/organizations/{orgId}/patients/{patientId}/report?month=2026-03  → Download PDF
```

### Observability & Docs
```
GET    /actuator/health        → {"status":"UP", components: db, redis, rabbit}
GET    /actuator/metrics       → Micrometer metrics
GET    /actuator/prometheus    → Prometheus scrape endpoint
GET    /swagger-ui.html        → Interactive API docs
GET    /v3/api-docs            → OpenAPI 3 JSON spec
WS     /ws                     → STOMP+SockJS → /topic/org/{orgId}/dashboard
```

### Anomaly Detection Thresholds
| Vital Type | Threshold | Event Published |
|-----------|-----------|-----------------|
| BLOOD_PRESSURE | systolic > 160 or diastolic > 100 | `BP_ANOMALY_DETECTED` |
| BLOOD_SUGAR | value > 250 mg/dL | `GLUCOSE_ANOMALY_DETECTED` |
| SPO2 | value < 92% | `SPO2_ANOMALY_DETECTED` |
| TEMPERATURE | value > 38.5°C | `TEMP_ANOMALY_DETECTED` |

---

## ✅ Progress Tracker

```
Sprint 1 — Foundation        [██████████] 100%  ✅ COMPLETE
Sprint 2 — Core APIs         [██████████] 100%  ✅ COMPLETE
Sprint 3 — Security          [██████████] 100%  ✅ COMPLETE
Sprint 4 — Medication Engine [██████████] 100%  ✅ COMPLETE
Sprint 5 — Performance       [██████████] 100%  ✅ COMPLETE
Sprint 6 — Product Features  [██████████] 100%  ✅ COMPLETE
Sprint 7 — Production        [██████████] 100%  ✅ COMPLETE

Overall: ████████████████████  100%  (7/7 sprints done)
App is live and running in Docker 🐳
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
| Real-time Updates | Polling every N seconds | **WebSocket STOMP** — push from `markDose()` in same transaction |
| Rate Limiting | None | **Bucket4j** token bucket, Redis-backed, 100 req/min per user |
| Performance | Load all rows every time | `@Cacheable` Redis (5 min TTL) + `@EntityGraph` N+1 fix |
| Audit Trail | None | `audit_logs` — append-only, **no FK constraints** (log outlives deleted data), `@Async` so it never blocks |
| Scheduling | Hardcoded `Thread.sleep` | `@Scheduled` CRON → materialise `dose_events` rows — simple indexed query at runtime |
| Duplicate Prevention | Nothing | `existsByScheduleIdAndScheduledAt()` idempotency check before every insert |
| Schema Changes | `ddl-auto: update` (dangerous) | `ddl-auto: validate` + **Flyway migrations** |
| PDF Reports | Manual stream with risk of leaking | `HttpServletResponse` streaming, `Content-Disposition: attachment` |
| Multi-tenancy | No isolation | `findByIdAndOrganizationId()` — tenant boundary enforced at **query level** |
| Error Handling | Random 500 HTML pages | `@RestControllerAdvice` — typed JSON errors with field-level validation messages |

---

## 🚀 Running Locally

### Prerequisites

- **Docker Desktop** — [download](https://www.docker.com/products/docker-desktop/)
- **Git**
- A **Neon account** (free) — [neon.tech](https://neon.tech) — for PostgreSQL
- A **Google Cloud** project with OAuth2 credentials — [console.cloud.google.com](https://console.cloud.google.com)

---

### Step 1 — Clone the repo

```bash
git clone https://github.com/dev-yash05/carecircle-backend.git
cd carecircle-backend
```

---

### Step 2 — Set up Neon (PostgreSQL)

1. Go to [console.neon.tech](https://console.neon.tech) → create a new project
2. Copy the **pooled connection string** (contains `-pooler` in the hostname)
3. It looks like:
   ```
   postgresql://neondb_owner:your_password@ep-xxxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```
4. Convert it to JDBC format:
   ```
   jdbc:postgresql://ep-xxxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
   ```

---

### Step 3 — Set up Google OAuth2

1. Go to [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials
2. Create OAuth 2.0 Client ID → Web application
3. Add Authorized redirect URI:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
4. Copy the **Client ID** and **Client Secret**

---

### Step 4 — Create your `.env` file

```bash
cp .env.example .env
```

Open `.env` and fill in your real values:

```env
DB_PASSWORD=your_neon_password
DB_URL=jdbc:postgresql://ep-xxxx-pooler.region.aws.neon.tech/neondb?sslmode=require&channel_binding=require
DB_USERNAME=neondb_owner

GOOGLE_CLIENT_ID=your_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_client_secret

JWT_SECRET=generate_with_openssl_rand_base64_64
JWT_EXPIRY_MS=900000
JWT_REFRESH_EXPIRY_MS=604800000

# --- Broker & Cache Config ---
RABBITMQ_USERNAME=carecircle
RABBITMQ_PASSWORD=your_rabbitmq_password

REDIS_PASSWORD=your_redis_password
SPRING_DATA_REDIS_PASSWORD=your_redis_password
```

Generate a strong JWT secret:
```bash
# macOS / Linux / WSL
openssl rand -base64 64

# Windows PowerShell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

Secure the file (macOS/Linux):
```bash
chmod 600 .env
```

---

### Step 5 — Build and start

```bash
# First run — builds the Spring Boot Docker image (takes 3-5 min)
docker compose up --build

# Or detached (runs in background)
docker compose up --build -d
```

Docker Compose starts services in order:
1. **Redis** → waits for `PONG` health check
2. **RabbitMQ** → waits for AMQP port health check
3. **App** → waits for both, then boots (Flyway runs migrations automatically)

---

### Step 6 — Verify everything is up

```bash
# Watch startup logs
docker compose logs -f app

# Look for this line:
# Started CarecircleBackendApplication in XX.XXX seconds

# Then check health
curl http://localhost:8080/actuator/health | jq .
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db":     { "status": "UP" },
    "redis":  { "status": "UP" },
    "rabbit": { "status": "UP" }
  }
}
```

---

### Step 7 — Open the API docs

Go to **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

To authenticate:
1. Go to `http://localhost:8080/oauth2/authorization/google`
2. Complete Google login
3. You'll be redirected — your `access_token` cookie is now set
4. All Swagger requests will automatically include it

---

### Useful commands

```bash
# Stop everything (volumes kept — data survives)
docker compose down

# Stop and wipe all data (fresh start)
docker compose down -v

# Rebuild after code changes
docker compose up --build -d

# View logs
docker compose logs -f app        # Spring Boot
docker compose logs -f rabbitmq   # RabbitMQ

# Check container status
docker compose ps

# RabbitMQ Management UI
# http://localhost:15672
# Login: carecircle / your_rabbitmq_password
```

---

## 🔐 Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_URL` | ✅ | Neon JDBC connection string (pooled) |
| `DB_USERNAME` | ✅ | Neon database username |
| `DB_PASSWORD` | ✅ | Neon database password |
| `GOOGLE_CLIENT_ID` | ✅ | Google OAuth2 Client ID |
| `GOOGLE_CLIENT_SECRET` | ✅ | Google OAuth2 Client Secret |
| `JWT_SECRET` | ✅ | HMAC-SHA256 signing key — min 32 chars, use `openssl rand -base64 64` |
| `JWT_EXPIRY_MS` | ✅ | Access token lifetime in ms (default: 900000 = 15 min) |
| `JWT_REFRESH_EXPIRY_MS` | ✅ | Refresh token lifetime in ms (default: 604800000 = 7 days) |
| `RABBITMQ_USERNAME` | ✅ | RabbitMQ container username |
| `RABBITMQ_PASSWORD` | ✅ | RabbitMQ container password |
| `REDIS_PASSWORD` | ✅ | Redis container password (also set on `redis-server --requirepass`) |
| `SPRING_DATA_REDIS_PASSWORD` | ✅ | Same as `REDIS_PASSWORD` — Spring reads this key specifically |

---

*SENIOR HIRING SIGNAL PROJECT · 7 SPRINTS · PRODUCTION GRADE*
*All sprints complete — running in Docker with Neon PostgreSQL, Redis, and RabbitMQ*
*Repo: https://github.com/dev-yash05/carecircle-backend*