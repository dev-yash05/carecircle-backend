-- =============================================================================
-- V2: Auth Redesign — SUPER_ADMIN role + pre-registered members
-- =============================================================================
-- What this migration does:
--   1. Adds SUPER_ADMIN to the role CHECK constraint
--   2. Makes google_subject_id nullable (pre-registered users have no sub yet)
--   3. Makes organization_id nullable (SUPER_ADMIN has no org)
--   4. Replaces the per-org email unique constraint with a global one
-- =============================================================================

-- Step 1: Drop the old role CHECK so we can replace it
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_role;

-- Step 2: Add the new role CHECK that includes SUPER_ADMIN
ALTER TABLE users ADD CONSTRAINT chk_role
    CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'CAREGIVER', 'VIEWER'));

-- Step 3: google_subject_id is already nullable in V1 (no NOT NULL constraint).
--         Confirming it stays nullable — pre-registered users have NULL here
--         until they first sign in with Google and we link their sub.
-- (No ALTER needed — column was created without NOT NULL in V1)

-- Step 4: Make organization_id nullable so SUPER_ADMIN can exist without an org
ALTER TABLE users ALTER COLUMN organization_id DROP NOT NULL;

-- Step 5: Drop the old per-org email uniqueness constraint
ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_user_email_per_org;

-- Step 6: Add a global email uniqueness constraint
--         A person's real-world email is unique across the entire system.
--         If Ramesh is a caregiver in org A and also in org B someday,
--         that would use a junction table (Sprint 7). For now, one org per email.
ALTER TABLE users ADD CONSTRAINT uq_user_email_global UNIQUE (email);

-- =============================================================================
-- Notes for the future:
--   - Sprint 7: replace the single org FK with a user_org_memberships junction
--     table to allow one user to belong to multiple organizations.
--   - Sprint 7: add a pending_invitations table for the full invite-token flow.
-- =============================================================================