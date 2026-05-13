-- V001__create_persistence_schema.sql
-- EPMICMPCOD-351: Persistence Schema for Execution Results
-- SRS §2.2 steps 12–14: Create submissions and submission_test_results tables
-- SRS §5 + §9: Domain model entities with proper constraints and indexes
-- SRS §12: Batch configuration (batch_size=50, order_inserts=true)

-- ============================================================================
-- Table: submissions
-- Purpose: Store code submission execution results
-- SRS §2.2 steps 12–14
-- ============================================================================
CREATE TABLE IF NOT EXISTS submissions (
    id BIGSERIAL PRIMARY KEY,
    
    -- Idempotency key: UUID from ExecutionResultEvent.executionId (SRS §5.2)
    execution_id UUID NOT NULL UNIQUE,
    
    -- User and Problem identifiers (SRS §2.2 domain model)
    user_id VARCHAR(64) NOT NULL,
    problem_id VARCHAR(128) NOT NULL,
    
    -- Execution metadata (SRS §2.2 domain model)
    language VARCHAR(32) NOT NULL,
    mode VARCHAR(16) NOT NULL,  -- RUN or SUBMIT
    
    -- Verdict and status (SRS §2.2 step 12: one of defined verdicts)
    verdict VARCHAR(32) NOT NULL,  -- PASSED, WRONG_ANSWER, COMPILE_ERROR, etc.
    status VARCHAR(32) NOT NULL,   -- COMPLETED, FAILED, TIMEOUT
    
    -- Optional scoring (SRS §9 domain model)
    score INTEGER,
    
    -- Performance metrics (SRS §9 domain model)
    total_runtime_ms BIGINT NOT NULL,
    memory_bytes BIGINT NOT NULL,
    
    -- Output and error messages (SRS §9 domain model, TEXT for large content)
    raw_output TEXT,
    error_output TEXT,
    
    -- Submitted code (SRS §9 domain model, TEXT for source code)
    submitted_code TEXT NOT NULL,
    
    -- Timestamps (SRS §2.2 step 11: execution timing)
    submitted_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    
    -- Database audit timestamps (JPA lifecycle hooks)
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ
);

-- ============================================================================
-- Table: submission_test_results
-- Purpose: Store individual test case execution results
-- SRS §2.2 steps 12–14: One-to-Many relationship with submissions
-- ============================================================================
CREATE TABLE IF NOT EXISTS submission_test_results (
    id BIGSERIAL PRIMARY KEY,
    
    -- Foreign key to parent execution (SRS §2.2 step 13)
    execution_id UUID NOT NULL REFERENCES submissions(execution_id) ON DELETE CASCADE,
    
    -- Test case identification (SRS §2.2 step 13)
    test_case_id VARCHAR(128) NOT NULL,
    
    -- Test case execution status (SRS §2.2 step 13: one of defined statuses)
    status VARCHAR(32) NOT NULL,  -- PASSED, FAILED, TIMEOUT, etc.
    
    -- Performance metrics per test case (SRS §9 domain model)
    runtime_ms BIGINT,
    memory_bytes BIGINT,
    
    -- Expected vs Actual output (SRS §2.2 step 13)
    expected_output TEXT,
    actual_output TEXT,
    
    -- Error diagnostics (SRS §9 domain model)
    error_output TEXT,
    
    -- Database audit timestamps (JPA lifecycle hooks)
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ
);

-- ============================================================================
-- Indexes: Query Performance and Foreign Key Optimization (SRS §9)
-- ============================================================================

-- Index for user submission history queries (SRS §2.2 user history)
CREATE INDEX IF NOT EXISTS idx_submissions_user_created_at 
    ON submissions(user_id, created_at DESC);

-- Index for problem submission history queries (SRS §2.2 problem history)
CREATE INDEX IF NOT EXISTS idx_submissions_problem_created_at 
    ON submissions(problem_id, created_at DESC);

-- Index for test result lookup by execution (SRS §5.2 join performance)
CREATE INDEX IF NOT EXISTS idx_submission_test_results_execution 
    ON submission_test_results(execution_id);

-- ============================================================================
-- Constraints: Data Integrity (SRS §5.2)
-- ============================================================================

-- Check constraint for verdict values (SRS §2.2 step 12)
ALTER TABLE submissions
    ADD CONSTRAINT check_verdict 
    CHECK (verdict IN ('PASSED', 'WRONG_ANSWER', 'TIME_LIMIT_EXCEEDED', 
                       'RUNTIME_ERROR', 'COMPILE_ERROR', 'MEMORY_LIMIT_EXCEEDED',
                       'OUTPUT_LIMIT_EXCEEDED', 'RUNTIME_EXCEPTION', 'UNKNOWN'));

-- Check constraint for status values (SRS §2.2 step 12)
ALTER TABLE submissions
    ADD CONSTRAINT check_status 
    CHECK (status IN ('COMPLETED', 'FAILED', 'TIMEOUT'));

-- Check constraint for mode values (SRS §2.2)
ALTER TABLE submissions
    ADD CONSTRAINT check_mode 
    CHECK (mode IN ('RUN', 'SUBMIT'));

-- Check constraint for test result status values (SRS §2.2 step 13)
ALTER TABLE submission_test_results
    ADD CONSTRAINT check_test_status 
    CHECK (status IN ('PASSED', 'FAILED', 'TIMEOUT', 'ERROR', 'SKIPPED'));

-- ============================================================================
-- Comments: Schema Documentation
-- ============================================================================
COMMENT ON TABLE submissions IS 'Execution results for code submissions. Each record represents a single user submission execution. SRS §2.2 steps 12–14.';
COMMENT ON COLUMN submissions.execution_id IS 'Idempotent execution ID (UUID). Used to prevent duplicate submissions. SRS §5.2.';
COMMENT ON COLUMN submissions.verdict IS 'Final execution verdict. One of: PASSED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILE_ERROR, etc. SRS §2.2 step 12.';
COMMENT ON COLUMN submissions.status IS 'Execution status. One of: COMPLETED, FAILED, TIMEOUT. SRS §2.2 step 12.';

COMMENT ON TABLE submission_test_results IS 'Individual test case results within a submission. One-to-Many relationship with submissions. SRS §2.2 steps 12–14.';
COMMENT ON COLUMN submission_test_results.execution_id IS 'Foreign key to parent execution. Indexed for join performance. SRS §5.2, §9.';
COMMENT ON COLUMN submission_test_results.status IS 'Test case status. One of: PASSED, FAILED, TIMEOUT, ERROR, SKIPPED. SRS §2.2 step 13.';
