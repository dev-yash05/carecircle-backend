-- =============================================================================
-- CareCircle: Production-Grade PostgreSQL Schema
-- Author: Built for a Senior Hiring Signal
-- Version: V1 (Flyway naming: V1__init_schema.sql)
-- =============================================================================
-- 🧠 SENIOR THINKING: We use UUID as primary keys, not SERIAL integers.
-- Why? Because when you later split into microservices, integer IDs from
-- different services will COLLIDE (user #5 vs medication #5). UUIDs are
-- globally unique. This is a common interview question.
-- =============================================================================

-- Enable the UUID generation extension (built into Postgres)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";


-- =============================================================================
-- MODULE 1: IDENTITY (Who are the users?)
-- =============================================================================

-- 🏢 ORGANIZATIONS (The "Tenant" Table — This is what makes it Multi-Tenant SaaS)
-- A "Circle" in the UI maps to an Organization in the DB.
-- An Old Age Home, a Hospital, or a Family each gets ONE row here.
-- SENIOR SIGNAL: This is the foundation of Row-Level Security (RLS).
CREATE TABLE organizations (
                               id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               name          VARCHAR(255) NOT NULL,                          -- e.g., "Sharma Family Circle"
                               plan_type     VARCHAR(50) NOT NULL DEFAULT 'FREE',            -- FREE | PREMIUM | ENTERPRISE
                               created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                               CONSTRAINT chk_plan_type CHECK (plan_type IN ('FREE', 'PREMIUM', 'ENTERPRISE'))
);

COMMENT ON TABLE organizations IS 'Top-level tenant. Every piece of data is scoped to an org.';


-- 👤 USERS (Admins, Caregivers, Doctors — all in one table)
-- SENIOR SIGNAL: We do NOT create separate tables for each role.
-- That is a common junior mistake. Roles are just a label on the user.
CREATE TABLE users (
                       id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,

                       email             VARCHAR(255) NOT NULL,
                       full_name         VARCHAR(255) NOT NULL,
                       avatar_url        TEXT,                                       -- From Google OAuth2 profile

    -- 🔐 OAuth2: We store the Google subject ID, NOT a password.
    -- If you allow email/password later, add a password_hash column.
                       google_subject_id VARCHAR(255) UNIQUE,                       -- The "sub" claim from Google's JWT

                       role              VARCHAR(50) NOT NULL DEFAULT 'CAREGIVER',  -- ADMIN | CAREGIVER | VIEWER
                       is_active         BOOLEAN NOT NULL DEFAULT TRUE,

                       created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- A user's email must be unique WITHIN an organization
                       CONSTRAINT uq_user_email_per_org UNIQUE (organization_id, email),
                       CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'CAREGIVER', 'VIEWER'))
);

COMMENT ON COLUMN users.google_subject_id IS 'Immutable ID from Google. Never changes even if user changes email.';


-- 🔑 REFRESH TOKENS (For JWT Refresh Rotation)
-- SENIOR SIGNAL: Never trust stateless JWTs alone for long sessions.
-- When a user logs out or we detect a breach, we DELETE their row here.
-- This is the server-side state that makes "Refresh Token Rotation" work.
CREATE TABLE refresh_tokens (
                                id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

                                token_hash      VARCHAR(255) NOT NULL UNIQUE,               -- Store HASH of token, never the raw value
                                expires_at      TIMESTAMPTZ NOT NULL,
                                is_revoked      BOOLEAN NOT NULL DEFAULT FALSE,

    -- 🔐 On refresh: set is_revoked=TRUE here AND insert a new row.
    -- If you see TWO active tokens for one user, it's a REPLAY ATTACK.
                                created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);


-- =============================================================================
-- MODULE 2: PATIENTS (The "Care Recipients")
-- =============================================================================

-- 👵 PATIENTS (The elderly person being cared for)
-- SENIOR SIGNAL: A Patient is NOT a User. They don't log in.
-- Mixing them would be a design mistake that causes pain later.
CREATE TABLE patients (
                          id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,

                          full_name        VARCHAR(255) NOT NULL,
                          date_of_birth    DATE NOT NULL,
                          gender           VARCHAR(20),
                          blood_type       VARCHAR(5),                                 -- A+, B-, O+, etc.

    -- JSONB for flexible, unstructured notes (allergies, emergency contacts)
    -- SENIOR SIGNAL: Don't create 10 nullable columns. Use JSONB for
    -- semi-structured data. It's indexable and queryable in Postgres.
                          metadata         JSONB NOT NULL DEFAULT '{}',               -- e.g., {"allergies": ["Penicillin"]}

                          is_active        BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN patients.metadata IS 'Flexible store for allergies, emergency contacts, doctor info.';
CREATE INDEX idx_patients_org ON patients(organization_id);


-- 🔗 CAREGIVER_PATIENT ASSIGNMENTS (Many-to-Many)
-- One caregiver can look after multiple patients.
-- One patient can have multiple caregivers (day shift, night shift).
CREATE TABLE caregiver_assignments (
                                       id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       caregiver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                       patient_id   UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                                       assigned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                       assigned_by  UUID REFERENCES users(id),                     -- Which Admin made this assignment

                                       CONSTRAINT uq_caregiver_patient UNIQUE (caregiver_id, patient_id)
);


-- =============================================================================
-- MODULE 3: MEDICATIONS (The "Brain" of CareCircle)
-- =============================================================================

-- 💊 MEDICATION SCHEDULES (The master template — "What med, how often?")
-- This is the "definition." It doesn't say WHEN; it says "every 12 hours."
CREATE TABLE medication_schedules (
                                      id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      patient_id       UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                                      created_by       UUID NOT NULL REFERENCES users(id),

                                      medication_name  VARCHAR(255) NOT NULL,                     -- "Metformin 500mg"
                                      dosage           VARCHAR(100) NOT NULL,                     -- "1 tablet"
                                      instructions     TEXT,                                      -- "Take after meals"

    -- Scheduling Logic
    -- SENIOR SIGNAL: Store the CRON expression. Don't hardcode "every 12 hours."
    -- This gives you infinite flexibility: daily, twice daily, every Monday, etc.
                                      cron_expression  VARCHAR(100) NOT NULL,                     -- e.g., "0 8,20 * * *" = 8AM and 8PM daily
                                      timezone         VARCHAR(100) NOT NULL DEFAULT 'Asia/Kolkata',

                                      start_date       DATE NOT NULL,
                                      end_date         DATE,                                      -- NULL = ongoing prescription

                                      is_active        BOOLEAN NOT NULL DEFAULT TRUE,
                                      created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                      updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN medication_schedules.cron_expression IS 'Standard CRON. Evaluated by Quartz Scheduler to generate dose_events.';
CREATE INDEX idx_med_schedules_patient ON medication_schedules(patient_id);
CREATE INDEX idx_med_schedules_active ON medication_schedules(is_active) WHERE is_active = TRUE;


-- 📅 DOSE EVENTS (The "instances" — "What needs to happen at 8 AM today?")
-- SENIOR SIGNAL: This is the KEY design decision that separates juniors from seniors.
-- The Quartz Scheduler reads `medication_schedules` and PRE-GENERATES these rows
-- for the next 24 hours. This makes querying "What's due now?" a simple
-- indexed lookup instead of complex CRON math at query time.
CREATE TABLE dose_events (
                             id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             schedule_id      UUID NOT NULL REFERENCES medication_schedules(id) ON DELETE CASCADE,
                             patient_id       UUID NOT NULL REFERENCES patients(id),     -- Denormalized for fast queries

                             scheduled_at     TIMESTAMPTZ NOT NULL,                      -- Exact time this dose is due

    -- Status machine: PENDING → TAKEN | MISSED | SKIPPED
                             status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Who acted on this dose and when
                             actioned_by      UUID REFERENCES users(id),
                             actioned_at      TIMESTAMPTZ,
                             notes            TEXT,                                      -- "Patient was sleeping, given 30 min late"

    -- 🔐 OPTIMISTIC LOCKING: Prevents two caregivers marking the same dose simultaneously
    -- Spring Data JPA uses this automatically with @Version annotation
                             version          INTEGER NOT NULL DEFAULT 0,

                             created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                             updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                             CONSTRAINT uq_schedule_time UNIQUE (schedule_id, scheduled_at),  -- No duplicate dose at same time
                             CONSTRAINT chk_dose_status CHECK (status IN ('PENDING', 'TAKEN', 'MISSED', 'SKIPPED'))
);

-- 🚀 PERFORMANCE INDEXES: This is what makes the dashboard load fast
-- "Give me all PENDING doses for patient X in the next 2 hours"
CREATE INDEX idx_dose_events_patient_status ON dose_events(patient_id, status, scheduled_at);
-- "Give me all PENDING doses across ALL patients" (for the Scheduler job)
CREATE INDEX idx_dose_events_pending ON dose_events(status, scheduled_at) WHERE status = 'PENDING';

COMMENT ON COLUMN dose_events.version IS 'Optimistic lock. JPA increments this on every UPDATE. Concurrent update throws ObjectOptimisticLockingFailureException.';


-- =============================================================================
-- MODULE 4: HEALTH TRACKER
-- =============================================================================

-- 📊 VITAL READINGS (Time-series health data)
-- SENIOR SIGNAL: Use JSONB for readings. Different vitals have different
-- structures (BP has systolic+diastolic, blood sugar is a single number).
-- One flexible table beats 5 rigid tables.
CREATE TABLE vital_readings (
                                id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                patient_id       UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
                                recorded_by      UUID NOT NULL REFERENCES users(id),

                                vital_type       VARCHAR(50) NOT NULL,                      -- BLOOD_PRESSURE | BLOOD_SUGAR | WEIGHT | SPO2
                                reading_value    JSONB NOT NULL,                            -- {"systolic": 130, "diastolic": 85} or {"value": 110, "unit": "mg/dL"}
                                measured_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),        -- When the reading was actually taken

                                is_anomalous     BOOLEAN NOT NULL DEFAULT FALSE,            -- Set to TRUE by the anomaly detection logic
                                alert_triggered  BOOLEAN NOT NULL DEFAULT FALSE,

                                created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                CONSTRAINT chk_vital_type CHECK (vital_type IN ('BLOOD_PRESSURE', 'BLOOD_SUGAR', 'WEIGHT', 'SPO2', 'TEMPERATURE'))
);

CREATE INDEX idx_vitals_patient_type_time ON vital_readings(patient_id, vital_type, measured_at DESC);
CREATE INDEX idx_vitals_anomalous ON vital_readings(patient_id, is_anomalous) WHERE is_anomalous = TRUE;

COMMENT ON COLUMN vital_readings.reading_value IS 'Flexible JSONB. Query with: reading_value->>''systolic'' > 160';


-- =============================================================================
-- MODULE 5: TRANSACTIONAL OUTBOX (The Reliability Pattern)
-- =============================================================================

-- 📤 OUTBOX TABLE (The heart of the Transactional Outbox Pattern)
-- SENIOR SIGNAL: This is what separates a "student project" from a
-- "production system." Here's the problem it solves:
--
-- BAD (Junior) approach:
--   1. UPDATE dose_events SET status='TAKEN'  ← DB transaction
--   2. rabbitTemplate.send(...)               ← Network call
--   If step 2 fails (network blip), the dose is marked TAKEN but NO
--   notification was ever sent. Data and reality are now out of sync.
--
-- GOOD (Senior) approach (Outbox Pattern):
--   In ONE atomic DB transaction:
--   1. UPDATE dose_events SET status='TAKEN'
--   2. INSERT INTO outbox (payload) VALUES (...)
--   A SEPARATE background job reads the outbox and sends to RabbitMQ.
--   If RabbitMQ is down, the job retries. Data is NEVER lost.
CREATE TABLE outbox_events (
                               id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               aggregate_type   VARCHAR(100) NOT NULL,                     -- "DoseEvent" | "VitalReading"
                               aggregate_id     UUID NOT NULL,                             -- The ID of the entity that changed
                               event_type       VARCHAR(100) NOT NULL,                     -- "DOSE_TAKEN" | "BP_ANOMALY_DETECTED"
                               payload          JSONB NOT NULL,                            -- Full event data for the consumer
                               status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',    -- PENDING | PROCESSED | FAILED
                               retry_count      INTEGER NOT NULL DEFAULT 0,
                               last_error       TEXT,                                      -- Store error message if processing failed
                               created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               processed_at     TIMESTAMPTZ
);

-- The background worker queries ONLY pending events, ordered by creation time
CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at) WHERE status = 'PENDING';

COMMENT ON TABLE outbox_events IS 'Transactional Outbox. Written in same DB txn as business data. Worker polls and relays to RabbitMQ.';


-- =============================================================================
-- MODULE 6: AUDIT LOG (Immutable History — Legal & Enterprise Requirement)
-- =============================================================================

-- 📋 AUDIT LOG (Every significant action is permanently recorded)
-- SENIOR SIGNAL: This table should NEVER be updated or deleted from.
-- It's an append-only log. This is what gives you legal defensibility.
-- "Did caregiver Ramesh mark the dose as taken?" — the audit log knows.
CREATE TABLE audit_logs (
                            id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            organization_id  UUID NOT NULL,                             -- No FK — log must survive even if org is deleted
                            actor_id         UUID,                                      -- No FK — log must survive even if user is deleted
                            actor_email      VARCHAR(255),                              -- Denormalized: capture it NOW in case user is deleted later

                            action           VARCHAR(100) NOT NULL,                     -- "DOSE_MARKED_TAKEN" | "PATIENT_CREATED" | "USER_LOGIN"
                            entity_type      VARCHAR(100),                              -- "DoseEvent" | "Patient"
                            entity_id        UUID,                                      -- The ID of the affected entity
                            old_value        JSONB,                                     -- What it looked like BEFORE
                            new_value        JSONB,                                     -- What it looks like AFTER

                            ip_address       INET,                                      -- Postgres native IP type
                            user_agent       TEXT,

                            created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()         -- The ONLY timestamp. No updated_at. Ever.
);

-- Fast lookup: "Show me all actions by user X" or "Show me history of dose Y"
CREATE INDEX idx_audit_actor ON audit_logs(actor_id, created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_org ON audit_logs(organization_id, created_at DESC);

COMMENT ON TABLE audit_logs IS 'Append-only. Never UPDATE or DELETE. No FK constraints intentional — log outlives the data it describes.';


-- =============================================================================
-- FLYWAY MIGRATION NOTES
-- =============================================================================
-- Save this file as: src/main/resources/db/migration/V1__init_schema.sql
-- Flyway will auto-run this on Spring Boot startup.
-- Next migration file: V2__seed_data.sql or V2__add_notification_preferences.sql
-- =============================================================================