-- V002__alter_submissions_ids_to_bigint.sql
-- Align column types with JPA entity (Long userId, Long problemId → BIGINT)
-- Required because V001 created user_id/problem_id as VARCHAR but entities define them as Long (BIGINT)

-- Drop dependent indexes first
DROP INDEX IF EXISTS idx_submissions_user_created_at;
DROP INDEX IF EXISTS idx_submissions_problem_created_at;

-- Alter user_id from VARCHAR to BIGINT
ALTER TABLE submissions
    ALTER COLUMN user_id TYPE BIGINT USING user_id::BIGINT;

-- Alter problem_id from VARCHAR to BIGINT
ALTER TABLE submissions
    ALTER COLUMN problem_id TYPE BIGINT USING problem_id::BIGINT;

-- Recreate indexes
CREATE INDEX IF NOT EXISTS idx_submissions_user_created_at
    ON submissions(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_submissions_problem_created_at
    ON submissions(problem_id, created_at DESC);

