-- schema.sql
-- CodEval Execution Engine — Consolidated Database Schema
-- SRS §5 Persistence Component | SRS §4.2 Caffeine cache DB fallback | SRS §9 Domain Model
-- Replaces Flyway migrations V001–V007 with a single authoritative DDL script.
--
-- DROP + CREATE ensures the schema is always aligned with the current entity model,
-- clearing any stale columns left by old conflicting Flyway migrations (e.g. V002 VARCHAR→BIGINT drift).
-- Safe for local dev; in production gate behind a one-time migration run or blue/green deploy.

-- ============================================================================
-- Drop existing tables (reverse FK order)
-- ============================================================================
DROP TABLE IF EXISTS submission_test_results CASCADE;
DROP TABLE IF EXISTS submissions CASCADE;
DROP TABLE IF EXISTS test_case CASCADE;

-- ============================================================================
-- Table: submissions
-- SRS §2.2 steps 12–14: Stores final aggregated execution verdict per submission.
-- SRS §5.2: Written via SubmissionRepository.save() after orchestrator aggregates verdict.
-- SRS §9: Domain model — executionId, userId (string slug), problemId (string slug),
--         language, mode, verdict, score (DOUBLE), totalRuntimeMs, memoryBytes.
-- SRS §12: Batch insert batch_size=50, order_inserts=true.
-- ============================================================================
CREATE TABLE submissions (
    id               BIGSERIAL    PRIMARY KEY,

    -- Idempotency key (SRS §5.2 — Kafka offset committed only after DB commit)
    execution_id     UUID         NOT NULL UNIQUE,

    -- SRS §9 domain model: userId and problemId are numeric Long identifiers
    user_id          BIGINT       NOT NULL,
    problem_id       BIGINT       NOT NULL,

    -- Human-readable problem name stored as a separate field (e.g. "Two Sum", "Reverse String")
    problem_name     VARCHAR(255),

    -- Execution metadata (SRS §9)
    language         VARCHAR(32)  NOT NULL,
    mode             VARCHAR(16)  NOT NULL,    -- RUN or SUBMIT

    -- Verdict and status (SRS §10 error-handling scenarios)
    verdict          VARCHAR(32)  NOT NULL,    -- PASSED, WRONG_ANSWER, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED
    status           VARCHAR(32)  NOT NULL,    -- COMPLETED, FAILED, TIMEOUT

    -- Optional scoring (SRS §9 — DOUBLE PRECISION maps to Java Double)
    score            DOUBLE PRECISION,

    -- Performance metrics (SRS §9 ExecutionResultEvent)
    total_runtime_ms BIGINT       NOT NULL,
    memory_bytes     BIGINT       NOT NULL,

    -- Output and diagnostics (SRS §9 — TEXT for large content)
    raw_output       TEXT,
    error_output     TEXT,

    -- Submitted source code (SRS §9)
    submitted_code   TEXT         NOT NULL,

    -- Timing (SRS §2.2 step 11)
    submitted_at     TIMESTAMPTZ  NOT NULL,
    completed_at     TIMESTAMPTZ,

    -- JPA audit timestamps
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ
);

-- ============================================================================
-- Table: submission_test_results
-- SRS §2.2 step 13: Individual per-test-case results under a submission.
-- SRS §9 TestCaseResultEvent: status, runtimeMs, memoryBytes, expectedOutput, actualOutput.
-- ============================================================================
CREATE TABLE submission_test_results (
    id               BIGSERIAL    PRIMARY KEY,

    -- FK to parent submission (cascade delete on submission removal)
    execution_id     UUID         NOT NULL REFERENCES submissions(execution_id) ON DELETE CASCADE,

    -- Test case identification (SRS §9)
    test_case_id     VARCHAR(128) NOT NULL,

    -- Per-test-case outcome (SRS §10: PASSED, FAILED, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED)
    status           VARCHAR(32)  NOT NULL,

    -- Per-test-case metrics (SRS §9)
    runtime_ms       BIGINT,
    memory_bytes     BIGINT,

    -- Diff data (SRS §9)
    expected_output  TEXT,
    actual_output    TEXT,
    error_output     TEXT,

    -- JPA audit timestamps
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ
);

-- ============================================================================
-- Table: test_case
-- SRS §4.2: Caffeine JVM-local cache (key=problemId) with PostgreSQL as fallback on miss.
-- SRS §9: problemId is a string slug (VARCHAR(128)), not a numeric ID.
-- ============================================================================
CREATE TABLE test_case (
    id               BIGSERIAL    PRIMARY KEY,

    -- SRS §9: problemId is a numeric Long identifier
    problem_id       BIGINT       NOT NULL,

    -- Human-readable problem name stored separately (e.g. "Two Sum")
    problem_name     VARCHAR(255),

    input            TEXT         NOT NULL,
    expected_output  TEXT         NOT NULL,

    -- SRS §12 app.execution.timeout-ms default: 3000
    timeout_ms       INTEGER      NOT NULL DEFAULT 3000
);

-- ============================================================================
-- Indexes — Query Performance (SRS §9, §4.2)
-- ============================================================================

-- Submission history by user (SRS §2.2 user history queries)
CREATE INDEX idx_submissions_user_created_at
    ON submissions(user_id, created_at DESC);

-- Submission history by problem (SRS §2.2 problem history queries)
CREATE INDEX idx_submissions_problem_created_at
    ON submissions(problem_id, created_at DESC);

-- FK join performance for test results (SRS §5.2)
CREATE INDEX idx_submission_test_results_execution
    ON submission_test_results(execution_id);

-- Caffeine cache DB fallback lookup (SRS §4.2 — key=problemId BIGINT)
CREATE INDEX idx_test_case_problem_id
    ON test_case(problem_id);
