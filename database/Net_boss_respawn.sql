-- Next scheduled occurrence for each configured boss placement. Safe to rerun.
CREATE TABLE IF NOT EXISTS boss_respawn_state (
    npcid INT NOT NULL,
    spawn_x INT NOT NULL,
    spawn_y INT NOT NULL,
    spawn_map INT NOT NULL,
    fingerprint VARBINARY(64) NOT NULL,
    slot_ms BIGINT NOT NULL,
    due_ms BIGINT NOT NULL,
    PRIMARY KEY (npcid, spawn_x, spawn_y, spawn_map)
) ENGINE=InnoDB;
