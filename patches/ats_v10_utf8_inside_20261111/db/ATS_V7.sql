-- Import into the GAME database before starting the V7 server.
CREATE TABLE IF NOT EXISTS character_ats_time (
    char_id INT NOT NULL,
    remaining_millis BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (char_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Example: set a character's balance to 24 hours while that character's ATS is OFF.
-- INSERT INTO character_ats_time (char_id, remaining_millis) VALUES (12345, 86400000)
-- ON DUPLICATE KEY UPDATE remaining_millis=VALUES(remaining_millis);
-- Replace 12345 with the actual character object ID. No daily reset is scheduled.
