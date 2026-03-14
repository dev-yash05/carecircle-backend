# 🏥 CareCircle Backend

> **Production-grade family caregiving platform — fully built and running.**
> Spring Boot 4 · PostgreSQL (Neon) · Redis · RabbitMQ · Docker · Java 21

[![Progress](https://img.shields.io/badge/Progress-Sprint%208%20Complete-brightgreen)](https://github.com/dev-yash05/carecircle-backend)
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
- [Role & Permission Matrix](#-role--permission-matrix)
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
| Total Sprints | 8 |
| **Current Status** | **All 8 Sprints Complete ✅** |
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
- New caregivers had **no way to join an existing org** — every Google login created a brand-new empty organization

CareCircle solves this with a scheduled, event-driven, real-time system that keeps every stakeholder in sync — with a proper multi-role auth system so Admins, Caregivers, and Viewers each access exactly what they need.

---

## 🌅 Real-World Use Case: "A Morning with Grandma"

> *Anjali (Admin, Bangalore), Ramesh (Caregiver, Sagar), and Priya (Viewer, Mumbai) caring for Grandma*

| Time | Actor | Event | Tech Behind It |
|------|-------|-------|----------------|
| 7:00 AM | `DoseEventScheduler` | Detects Grandma's 8 AM sugar medicine via CRON | `@Scheduled(cron="0 0 * * * *")` → `medication_schedules` |
| 7:01 AM | System | Pre-generates `DoseEvent` row (PENDING) | Idempotency check before insert |
| 7:05 AM | `OutboxPublisher` | Polls `outbox_events`, publishes to RabbitMQ | `@Scheduled(fixedDelay=5000)` → `carecircle.events` exchange |
| 8:00 AM | Anjali | Pre-registers Ramesh by email via API | `POST /organizations/{id}/members` → User row created, googleSubjectId=NULL |
| 8:10 AM | Ramesh | Signs in with Google for the first time | loadUser() Path 3: findByEmail → links Google sub, keeps CAREGIVER role + org |
| 8:16 AM | Ramesh | Sees "Pending Medication" dashboard | `GET /patients/{id}/doses` (paginated, cached) |
| 8:20 AM | Ramesh | Marks medicine as "TAKEN" | `PUT /doses/{id}/mark` → Optimistic Locking + Outbox write |
| 8:21 AM | Anjali | Dashboard syncs live ✅ | WebSocket STOMP → `/topic/org/{orgId}/dashboard` |
| 9:00 AM | Ramesh | Records BP reading | `POST /vitals` → anomaly detection → `BP_ANOMALY_DETECTED` outbox event |
| 9:01 AM | Priya | Views vitals read-only | VIEWER role — can read, cannot write |

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
| Migrations | Flyway | — | Versioned schema evolution (V1 + V2) |
| Auth | Spring Security + OAuth2 Google | — | Google login, JWT, 4-role RBAC |
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
│  Google OAuth2 → 4-path loadUser() → JWT (HttpOnly Cookie)   │
│  RBAC: SUPER_ADMIN > ADMIN > CAREGIVER > VIEWER              │
│  WebSocket STOMP → /topic/org/{orgId}/dashboard              │
└──┬──────────┬──────────┬──────────┬──────────┬──────────────┘
   │          │          │          │          │
Patient    Org+Member  Medication  Vital     Audit
Module    Module       Module      Module    Module
   │          │    (Schedule+         │         │
   └──────────┴── DoseEvent) ─────────┴─────────┘
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

### 4-Path OAuth2 loadUser() Flow (Sprint 8)

```
Google login arrives (sub + email)
│
├── 1. email == SUPER_ADMIN_EMAIL env var?
│        YES → create/update SUPER_ADMIN user (no org), done ✓
│
├── 2. findByGoogleSubjectId(sub) found?
│        YES → returning user, refresh name+avatar, done ✓  [hot path]
│
├── 3. findByEmail(email) found?
│        YES → pre-registered member (Admin added them before first login)
│              link Google sub now, keep CAREGIVER/VIEWER role + org, done ✓
│
└── 4. Nothing found?
         → new org auto-created + ADMIN user (self-registration), done ✓
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
    ├── CarecircleBackendApplication.java
    │
    ├── config/
    │   ├── AppProperties.java              # @ConfigurationProperties(prefix="app") — super-admin-email  🆕
    │   ├── JpaConfig.java
    │   ├── JwtProperties.java              # @ConfigurationProperties(prefix="app.jwt")
    │   ├── RabbitMQConfig.java
    │   ├── SecurityConfig.java             # Updated: SUPER_ADMIN routes, member routes  🆕
    │   ├── WebSocketConfig.java
    │   └── OpenApiConfig.java
    │
    ├── domain/
    │   ├── audit/
    │   │
    │   ├── medication/
    │   │
    │   ├── member/                         # 🆕 NEW PACKAGE
    │   │   ├── MemberController.java       # POST/GET/DELETE /organizations/{id}/members
    │   │   ├── MemberService.java          # Pre-registration logic + org isolation checks
    │   │   └── dto/MemberDto.java          # CreateRequest(email, role) + Response
    │   │
    │   ├── organization/
    │   │
    │   ├── outbox/
    │   │
    │   ├── patient/
    │   │
    │   ├── report/
    │   │
    │   ├── superadmin/                     # 🆕 NEW PACKAGE
    │   │   ├── SuperAdminController.java   # /superadmin/** — all orgs, all users, deactivate
    │   │   ├── SuperAdminService.java      # Cross-org queries, org/user deactivation
    │   │   └── dto/SuperAdminDto.java      # OrgSummary, OrgDetail, UserSummary, MemberSummary
    │   │
    │   ├── user/
    │   │   ├── User.java                   # Updated: Role enum adds SUPER_ADMIN, nullable org  🆕
    │   │   ├── RefreshToken.java
    │   │   └── RefreshTokenRepository.java
    │   │
    │   └── vital/
    │
    ├── scheduler/
    │   ├── DoseEventScheduler.java
    │   └── OutboxPublisher.java
    │
    ├── security/
    │   ├── CareCircleOAuth2UserService.java # Rewritten: 4-path loadUser()  🆕
    │   ├── JwtAuthFilter.java              # Updated: handles null org (SUPER_ADMIN)  🆕
    │   ├── JwtService.java                 # Updated: nullable orgId in token claims  🆕
    │   ├── OAuth2SuccessHandler.java       # Updated: role-based redirect, null org  🆕
    │   ├── AuthService.java                # Updated: null org in token rotation  🆕
    │   └── AuthController.java             # Updated: /me omits organizationId for SUPER_ADMIN  🆕
    │
    └── shared/
        ├── BaseEntity.java
        ├── RateLimitFilter.java
        └── exception/
            ├── GlobalExceptionHandler.java
            └── ResourceNotFoundException.java

└── src/main/resources/
    ├── application.yaml                    # Updated: app.super-admin-email  🆕
    ├── application-prod.yml
    └── db/migration/
        ├── V1__init_schema.sql             # 10 tables — original schema
        └── V2__add_super_admin_and_member_invite.sql  # 🆕 SUPER_ADMIN role, nullable org FK, global email unique
```

---

## 🗄 Database Schema

### V1 — Original (10 tables)

| Table | Module | Key Design Decisions |
|-------|--------|----------------------|
| `organizations` | Identity | Multi-tenant anchor. `plan_type` CHECK (FREE/PREMIUM/ENTERPRISE) |
| `users` | Identity | `google_subject_id` UNIQUE, `role` CHECK, global unique email |
| `refresh_tokens` | Identity | `token_hash` SHA-256 (never raw token), `is_revoked` |
| `patients` | Care | `metadata JSONB`, soft-delete via `is_active` |
| `caregiver_assignments` | Care | Junction table — UNIQUE(caregiver_id, patient_id) |
| `medication_schedules` | Medication | `cron_expression`, `timezone`, `start_date/end_date` |
| `dose_events` | Medication | `version INTEGER` (optimistic lock), UNIQUE(schedule_id, scheduled_at) |
| `vital_readings` | Health | `reading_value JSONB`, `is_anomalous`, `alert_triggered` |
| `outbox_events` | Messaging | `payload JSONB`, `retry_count`, `last_error`, partial index on PENDING |
| `audit_logs` | Audit | No FK constraints (log outlives data), `ip_address INET`, append-only |

### V2 — Auth Redesign (migration: `V2__add_super_admin_and_member_invite.sql`)

| Change | Before | After |
|--------|--------|-------|
| `role` CHECK constraint | `ADMIN`, `CAREGIVER`, `VIEWER` | + `SUPER_ADMIN` |
| `organization_id` on users | `NOT NULL` | Nullable — SUPER_ADMIN has no org |
| Email uniqueness | `UNIQUE(organization_id, email)` per org | `UNIQUE(email)` globally |

---

## 🔌 API Endpoints

### Auth
```
GET    /login/oauth2/code/google                   → Google OAuth2 callback → sets JWT cookies
POST   /api/v1/auth/refresh                        → Rotate refresh token
POST   /api/v1/auth/logout                         → Revoke tokens + clear cookies
GET    /api/v1/auth/me                             → Current user (SUPER_ADMIN omits organizationId)
```

### Organization
```
POST   /api/v1/organizations                       → Create org
GET    /api/v1/organizations/{id}                  → Get org
```

### Member Management 🆕
```
POST   /api/v1/organizations/{orgId}/members              → Pre-register caregiver/viewer by email  [ADMIN, SUPER_ADMIN]
GET    /api/v1/organizations/{orgId}/members              → List all active members                 [ADMIN, SUPER_ADMIN]
DELETE /api/v1/organizations/{orgId}/members/{userId}     → Deactivate member (soft)                [ADMIN, SUPER_ADMIN]
```

### Patient
```
POST   /api/v1/organizations/{orgId}/patients
GET    /api/v1/organizations/{orgId}/patients
GET    /api/v1/organizations/{orgId}/patients/{patientId}
PUT    /api/v1/organizations/{orgId}/patients/{patientId}
DELETE /api/v1/organizations/{orgId}/patients/{patientId}
```

### Medication
```
POST   /api/v1/organizations/{orgId}/medications                                   → Create schedule [ADMIN only]
GET    /api/v1/organizations/{orgId}/patients/{patientId}/medications              → List active schedules [@Cacheable]
GET    /api/v1/organizations/{orgId}/patients/{patientId}/doses                   → List dose events (paginated)
PUT    /api/v1/organizations/{orgId}/doses/{doseEventId}/mark                     → Mark TAKEN/SKIPPED → WebSocket push
```

### Vitals
```
POST   /api/v1/organizations/{orgId}/patients/{patientId}/vitals   → Record reading
GET    /api/v1/organizations/{orgId}/patients/{patientId}/vitals   → List (?vitalType=BLOOD_PRESSURE)
```

### Audit & Reports
```
GET    /api/v1/organizations/{orgId}/patients/{patientId}/audit    → Audit log (paginated)
GET    /api/v1/organizations/{orgId}/patients/{patientId}/report?month=2026-03  → Download PDF
```

### SUPER_ADMIN Panel 🆕
```
GET    /api/v1/superadmin/organizations              → All orgs paginated (member count, patient count)
GET    /api/v1/superadmin/organizations/{id}         → Org detail with full member list
GET    /api/v1/superadmin/users                      → All users across all orgs (paginated)
DELETE /api/v1/superadmin/organizations/{id}         → Deactivate entire org + all members
DELETE /api/v1/superadmin/users/{id}                 → Deactivate any individual user
```

### Observability & Docs
```
GET    /actuator/health        → {"status":"UP", components: db, redis, rabbit}
GET    /actuator/metrics       → Micrometer metrics
GET    /actuator/prometheus    → Prometheus scrape endpoint
GET    /swagger-ui.html        → Interactive API docs
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

## 🔐 Role & Permission Matrix

| Action | SUPER_ADMIN | ADMIN | CAREGIVER | VIEWER |
|--------|:-----------:|:-----:|:---------:|:------:|
| View all orgs & users (god-view) | ✅ | — | — | — |
| Deactivate any org or user | ✅ | — | — | — |
| Add / remove org members | ✅ | ✅ | — | — |
| Create / edit patients | ✅ | ✅ | — | — |
| Create medication schedules | ✅ | ✅ | — | — |
| Mark doses / record vitals | ✅ | ✅ | ✅ | — |
| View patients, doses, vitals | ✅ | ✅ | ✅ | ✅ |
| Receive WebSocket alerts | ✅ | ✅ | ✅ | ✅ |

**How roles are assigned:**
- `SUPER_ADMIN` — set via `SUPER_ADMIN_EMAIL` env var. Whoever owns that Gmail becomes god-mode on first login. No UI, no database change needed.
- `ADMIN` — any new Google login that isn't pre-registered and isn't SUPER_ADMIN. Gets a fresh org auto-created.
- `CAREGIVER` / `VIEWER` — Admin calls `POST /organizations/{id}/members` with their email. They log in with Google and land in the correct org automatically.

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
Sprint 8 — Auth Redesign     [██████████] 100%  ✅ COMPLETE

Overall: ████████████████████  100%  (8/8 sprints done)
App is live and running in Docker 🐳
```

### Sprint 8 — What was built

| # | Change | Detail |
|---|--------|--------|
| 1 | `SUPER_ADMIN` role | New top-level role above ADMIN. Set via env var, not database. |
| 2 | 4-path `loadUser()` | Rewrote `CareCircleOAuth2UserService` to handle all login scenarios correctly. |
| 3 | Member pre-registration | `POST /organizations/{id}/members` — Admin adds someone by email before they log in. |
| 4 | Member management API | Full `GET` (list) and `DELETE` (deactivate) for org members. |
| 5 | SUPER_ADMIN panel API | 5 new endpoints for god-view: all orgs, all users, deactivate any. |
| 6 | Nullable org in JWT chain | SUPER_ADMIN has no org — updated JWT, filter, success handler, and `/me` endpoint. |
| 7 | V2 Flyway migration | Adds SUPER_ADMIN to role constraint, makes org FK nullable, makes email globally unique. |

---

## 🧠 Key Engineering Decisions (Hiring Signals)

| Concern | Junior Approach | CareCircle Approach |
|---------|----------------|---------------------|
| Auth | Username + password in body | Google OAuth2 + JWT in `HttpOnly; Secure` Cookie |
| Token Storage | `localStorage` (XSS-vulnerable) | HttpOnly Cookie — JS can **never** read it |
| **Caregiver onboarding** | **Every login creates a new org** | **4-path loadUser(): pre-registered users link their Google sub on first login** |
| **SUPER_ADMIN** | **Hardcoded in DB or config** | **Env-var driven — no DB change, no deployment risk** |
| Concurrent Updates | Last-write-wins | `@Version` Optimistic Locking → `ObjectOptimisticLockingFailureException` → 409 |
| Notification Reliability | Direct `rabbitTemplate.send()` — can lose on crash | **Transactional Outbox** — atomic DB write + background relay |
| Refresh Token Security | Store raw token | Store **SHA-256 hash**, revoke-all on every new login |
| Real-time Updates | Polling every N seconds | **WebSocket STOMP** — push from `markDose()` in same transaction |
| Rate Limiting | None | **Bucket4j** token bucket, Redis-backed, 100 req/min per user |
| Performance | Load all rows every time | `@Cacheable` Redis (5 min TTL) + `@EntityGraph` N+1 fix |
| Audit Trail | None | `audit_logs` — append-only, **no FK constraints** (log outlives deleted data), `@Async` so it never blocks |
| Scheduling | Hardcoded `Thread.sleep` | `@Scheduled` CRON → materialise `dose_events` rows — simple indexed query at runtime |
| Duplicate Prevention | Nothing | `existsByScheduleIdAndScheduledAt()` idempotency check before every insert |
| Schema Changes | `ddl-auto: update` (dangerous) | `ddl-auto: validate` + **Flyway migrations** (V1 + V2) |
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
3. Convert it to JDBC format:
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

Open `.env` and fill in your values:

```env
DB_PASSWORD=your_neon_password
DB_URL=jdbc:postgresql://ep-xxxx-pooler.region.aws.neon.tech/neondb?sslmode=require&channel_binding=require
DB_USERNAME=neondb_owner

GOOGLE_CLIENT_ID=your_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_client_secret

JWT_SECRET=generate_with_openssl_rand_base64_64
JWT_EXPIRY_MS=900000
JWT_REFRESH_EXPIRY_MS=604800000

RABBITMQ_USERNAME=carecircle
RABBITMQ_PASSWORD=your_rabbitmq_password

REDIS_PASSWORD=your_redis_password
SPRING_DATA_REDIS_PASSWORD=your_redis_password

# The Google account that gets SUPER_ADMIN role automatically
SUPER_ADMIN_EMAIL=your.personal@gmail.com
```

Generate a strong JWT secret:
```bash
# macOS / Linux / WSL
openssl rand -base64 64

# Windows PowerShell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

---

### Step 5 — Build and start

```bash
# First run — builds the Docker image (takes 3–5 min)
docker compose up --build

# Detached
docker compose up --build -d
```

Flyway runs both migrations automatically on startup:
```
Migrating schema to version 1 - init schema
Migrating schema to version 2 - add super admin and member invite
Started CarecircleBackendApplication in XX.XXX seconds
```

---

### Step 6 — Verify

```bash
curl http://localhost:8080/actuator/health | jq .
```

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

### Step 7 — Test the auth flows

```bash
# 1. Open in browser → sign in with your SUPER_ADMIN_EMAIL Google account
http://localhost:8080/oauth2/authorization/google

# 2. Check /me → should return role: SUPER_ADMIN, no organizationId
curl http://localhost:8080/api/v1/auth/me -b "access_token=YOUR_TOKEN"

# 3. Pre-register a caregiver (as ADMIN or SUPER_ADMIN)
curl -X POST http://localhost:8080/api/v1/organizations/{ORG_ID}/members \
  -H "Content-Type: application/json" \
  -b "access_token=YOUR_TOKEN" \
  -d '{"email": "ramesh@gmail.com", "role": "CAREGIVER"}'

# 4. Have ramesh@gmail.com sign in with Google → they land as CAREGIVER in the correct org
```

---

### Useful commands

```bash
# Stop (data survives)
docker compose down

# Full reset (wipes Redis + RabbitMQ data)
docker compose down -v

# Rebuild after code changes
docker compose up --build -d

# Logs
docker compose logs -f app

# RabbitMQ Management UI → http://localhost:15672
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
| `REDIS_PASSWORD` | ✅ | Redis container password |
| `SPRING_DATA_REDIS_PASSWORD` | ✅ | Same as `REDIS_PASSWORD` — Spring reads this key specifically |
| `SUPER_ADMIN_EMAIL` | ✅ | Google email that gets SUPER_ADMIN role automatically on first login |

---

*SENIOR HIRING SIGNAL PROJECT · 8 SPRINTS · PRODUCTION GRADE*
*All sprints complete — running in Docker with Neon PostgreSQL, Redis, and RabbitMQ*
*Repo: https://github.com/dev-yash05/carecircle-backend*