-- V004__create_test_case_table.sql
-- Create test_case table for TestCaseEntity (SRS §4.2 — Caffeine cache with DB fallback)
-- Test cases are read-only during execution; fetched and cached in Caffeine (app.cache.testcase-ttl-minutes)

CREATE TABLE IF NOT EXISTS test_case (
    id           BIGSERIAL PRIMARY KEY,
    problem_id   BIGINT    NOT NULL,
    input        TEXT      NOT NULL,
    expected_output TEXT   NOT NULL,
    timeout_ms   INTEGER   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_problem_id
    ON test_case(problem_id);

