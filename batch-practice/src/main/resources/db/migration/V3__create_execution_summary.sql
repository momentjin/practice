CREATE TABLE settlement_execution_summary (
    job_execution_id BIGINT NOT NULL PRIMARY KEY,
    job_instance_id BIGINT NOT NULL,
    dataset VARCHAR(24) NOT NULL,
    target_count BIGINT NOT NULL,
    resume_from_count BIGINT NOT NULL,
    inserted_count BIGINT NOT NULL DEFAULT 0,
    existing_count BIGINT NOT NULL DEFAULT 0,
    skipped_count BIGINT NOT NULL DEFAULT 0,
    inserted_payout_amount BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6),
    elapsed_millis BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_summary_target CHECK (target_count >= 0 AND resume_from_count >= 0),
    CONSTRAINT ck_summary_counts CHECK (inserted_count >= 0 AND existing_count >= 0 AND skipped_count >= 0)
);
CREATE INDEX idx_summary_instance ON settlement_execution_summary(job_instance_id);
