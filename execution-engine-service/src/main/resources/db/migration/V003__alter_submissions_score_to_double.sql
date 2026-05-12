-- V003__alter_submissions_score_to_double.sql
-- Align score column type: INTEGER → DOUBLE PRECISION (maps to Java Double)
-- Required because V001 created score as INTEGER but SubmissionEntity defines it as Double

ALTER TABLE submissions
    ALTER COLUMN score TYPE DOUBLE PRECISION USING score::DOUBLE PRECISION;

