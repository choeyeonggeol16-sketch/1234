-- Per-character Play Support daily allowance and active lease.
-- InnoDB serializes lease updates on the character's primary key.
CREATE TABLE IF NOT EXISTS pss_daily_usage (
    char_id INT NOT NULL,
    window_start_ms BIGINT NOT NULL,
    used_ms BIGINT NOT NULL,
    lease_token BIGINT NOT NULL,
    lease_start_ms BIGINT NOT NULL,
    lease_end_ms BIGINT NOT NULL,
    PRIMARY KEY (char_id)
) ENGINE=InnoDB;
