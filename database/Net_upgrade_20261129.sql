-- RKern Net consolidated database upgrade: 2026-11-29 (KST)
-- Source revision: 2b3783656214d57603c5945f24181e7f8ee65244
-- EXISTING DB UPGRADE ONLY. This is NOT a complete Net.sql database dump.
-- Stop the game server, CacheD and external DB writers. Back up the DB.
-- Select the intended schema (USE ...); run on a test copy before production.
-- MySQL 5.7 / MariaDB. DDL implicitly commits; the entire file is not atomic.
-- Contains the existing, explicitly requested retired pet-table and npc.count_map
-- removals. Check dependencies and preserve historical data in a backup first.
-- Do not run individual scripts again after applying this consolidated alternative.
-- A complete dump containing account/character data is not published here.
-- Components below are verbatim SQL from the pinned source revision.

SET NAMES utf8mb4;

-- ===== 01. CacheD_transaction_receipts_migration.sql (blob dee2ffd07699f845995c6d9b6d0dbd01fa7c6a43) =====
-- Run on a TEST DATABASE first, then deploy to the game DB during maintenance.
-- InnoDB is mandatory: receipt and all affected item tables MUST be transactional.
CREATE TABLE IF NOT EXISTS cached_transaction_receipts (
    tx_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kind VARCHAR(16) NOT NULL,
    owner_a INT NOT NULL,
    owner_b INT NOT NULL,
    committed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tx_id),
    KEY idx_receipt_owner (owner_a, committed_at)
) ENGINE=InnoDB;

-- Every table named below must exist and use ENGINE=InnoDB.
-- Inspect the output; migrate a non-InnoDB table separately with a tested backup.
SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN
 ('cached_transaction_receipts', 'character_items', 'character_warehouse',
  'clan_warehouse', 'character_elf_warehouse',
  'character_special_warehouse', 'character_supplementary_service');

-- ===== 02. CacheD_economy_credit_migration.sql (blob a3fea220a6c8ccf70bd781f420070f2b3e7ce6b3) =====
-- Apply on the validation database before enabling durable auction refunds.
-- All tables participating in an economy receipt must use InnoDB.
-- Missing/nontransactional ledger causes the payer transaction to fail before
-- any fee or item change. Never truncate this table to clear an error.
CREATE TABLE IF NOT EXISTS character_pending_credits (
  credit_id CHAR(36) NOT NULL,
  char_id INT NOT NULL,
  amount INT NOT NULL,
  reason VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (credit_id),
  KEY pending_credit_owner (char_id),
  CONSTRAINT pending_credit_positive CHECK (amount > 0)
) ENGINE=InnoDB;

-- ===== 03. CacheD_economy_tables_migration.sql (blob 259842a46cbdf1493ed8b1e36c538f59e296be78) =====
-- New economy receipts also update these existing game tables. Apply to the
-- validation database before enabling this source revision; missing tables
-- mean the complete original schema has not been installed.
-- ALTER TABLE rebuilds tables and implicitly commits on MySQL. This is not a
-- runtime install script and the server never silently converts production DBs.
-- Keep the earlier CacheD_transaction_receipts_migration.sql and apply the new
-- CacheD_economy_credit_migration.sql too. Preserve all pending credit rows.
ALTER TABLE accounts ENGINE=InnoDB;
ALTER TABLE characters ENGINE=InnoDB;
ALTER TABLE character_items ENGINE=InnoDB;
ALTER TABLE character_shop ENGINE=InnoDB;
ALTER TABLE letter ENGINE=InnoDB;
ALTER TABLE clan_data ENGINE=InnoDB;
ALTER TABLE castle ENGINE=InnoDB;
ALTER TABLE house ENGINE=InnoDB;
ALTER TABLE board_auction ENGINE=InnoDB;
ALTER TABLE character_skills ENGINE=InnoDB;
ALTER TABLE character_teleport ENGINE=InnoDB;
ALTER TABLE character_teleport_item ENGINE=InnoDB;
ALTER TABLE character_tams ENGINE=InnoDB;
ALTER TABLE character_buddys ENGINE=InnoDB;
ALTER TABLE character_buff ENGINE=InnoDB;
ALTER TABLE character_config ENGINE=InnoDB;
ALTER TABLE character_quests ENGINE=InnoDB;
-- Existing account-shop quota ledger: purchases lock the parent accounts row
-- and reject duplicate (account,npc_id,item_id) rows; no new unique key assumed.
ALTER TABLE `상점제한유저정보` ENGINE=InnoDB;
ALTER TABLE tb_kda ENGINE=InnoDB;

-- ===== 04. CacheD_storage_schema_migration.sql (blob 1a62dca4d43ef3072539a885e287602436d697ac) =====
-- Select the RKern database before running this file.
-- Apply after importing the clean schema or upgrading the existing economy
-- tables. On an existing database, stop the server and take a backup first.
-- This additive migration is safe to repeat on both MySQL 5.7 and MariaDB.
-- The character column is also covered by rkern/sql/character_hp_mp_growth.sql.
SET @rkern_storage_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'characters'
          AND COLUMN_NAME = 'hp_mp_growth'
    ),
    'DO 0',
    'ALTER TABLE `characters` ADD COLUMN `hp_mp_growth` TEXT NULL'
);
PREPARE rkern_storage_stmt FROM @rkern_storage_ddl;
EXECUTE rkern_storage_stmt;
DEALLOCATE PREPARE rkern_storage_stmt;

-- Warehouse rows use the same quality column as the inventory row. Legacy
-- items therefore load the same zero default they had before migration.
SET @rkern_quality_column = 'ADD COLUMN `bless_level2` TINYINT(255) UNSIGNED NOT NULL DEFAULT 0';

SET @rkern_storage_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'character_warehouse'
          AND COLUMN_NAME = 'bless_level2'),
    'DO 0', CONCAT('ALTER TABLE `character_warehouse` ', @rkern_quality_column)
);
PREPARE rkern_storage_stmt FROM @rkern_storage_ddl;
EXECUTE rkern_storage_stmt;
DEALLOCATE PREPARE rkern_storage_stmt;

SET @rkern_storage_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'character_elf_warehouse'
          AND COLUMN_NAME = 'bless_level2'),
    'DO 0', CONCAT('ALTER TABLE `character_elf_warehouse` ', @rkern_quality_column)
);
PREPARE rkern_storage_stmt FROM @rkern_storage_ddl;
EXECUTE rkern_storage_stmt;
DEALLOCATE PREPARE rkern_storage_stmt;

SET @rkern_storage_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clan_warehouse'
          AND COLUMN_NAME = 'bless_level2'),
    'DO 0', CONCAT('ALTER TABLE `clan_warehouse` ', @rkern_quality_column)
);
PREPARE rkern_storage_stmt FROM @rkern_storage_ddl;
EXECUTE rkern_storage_stmt;
DEALLOCATE PREPARE rkern_storage_stmt;

SET @rkern_storage_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'character_special_warehouse'
          AND COLUMN_NAME = 'bless_level2'),
    'DO 0', CONCAT('ALTER TABLE `character_special_warehouse` ', @rkern_quality_column)
);
PREPARE rkern_storage_stmt FROM @rkern_storage_ddl;
EXECUTE rkern_storage_stmt;
DEALLOCATE PREPARE rkern_storage_stmt;

SET @rkern_storage_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'character_supplementary_service'
          AND COLUMN_NAME = 'bless_level2'),
    'DO 0', CONCAT('ALTER TABLE `character_supplementary_service` ', @rkern_quality_column)
);
PREPARE rkern_storage_stmt FROM @rkern_storage_ddl;
EXECUTE rkern_storage_stmt;
DEALLOCATE PREPARE rkern_storage_stmt;

-- The package warehouse also needs the base level fields. Add all missing
-- fields together so the table need only be altered once.
SET @rkern_package_columns = CONCAT_WS(', ',
    IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'character_package_warehouse'
              AND COLUMN_NAME = 'bless_level'),
        NULL, 'ADD COLUMN `bless_level` TINYINT(1) UNSIGNED NOT NULL DEFAULT 0'
    ),
    IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'character_package_warehouse'
              AND COLUMN_NAME = 'item_level'),
        NULL, 'ADD COLUMN `item_level` TINYINT(1) UNSIGNED NOT NULL DEFAULT 0'
    ),
    IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'character_package_warehouse'
              AND COLUMN_NAME = 'bless_level2'),
        NULL, @rkern_quality_column
    )
);
SET @rkern_storage_ddl = IF(@rkern_package_columns = '', 'DO 0',
    CONCAT('ALTER TABLE `character_package_warehouse` ', @rkern_package_columns));
PREPARE rkern_storage_stmt FROM @rkern_storage_ddl;
EXECUTE rkern_storage_stmt;
DEALLOCATE PREPARE rkern_storage_stmt;

CREATE TABLE IF NOT EXISTS `character_level_gifts` (
    `char_id` INT NOT NULL,
    `gift_level` INT NOT NULL,
    PRIMARY KEY (`char_id`, `gift_level`)
) ENGINE=InnoDB;

SET @rkern_storage_ddl = NULL, @rkern_package_columns = NULL, @rkern_quality_column = NULL;

-- ===== 05. CacheD_owner_lookup_indexes.sql (blob 856df382c1737d5a5c1b53fd2ffbd49d5de8e9f3) =====
-- Optional indexes for clan membership/ownership and mailbox lookups.
-- Run against the selected game schema during maintenance. The statements are replayable.
-- An existing B-tree with the same full leading columns is sufficient.

SET @rkern_owner_index_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'characters' AND INDEX_TYPE = 'BTREE'
     GROUP BY INDEX_NAME
    HAVING SUM(CASE WHEN SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'ClanID'
                         AND SUB_PART IS NULL THEN 1 ELSE 0 END) = 1
), 'DO 0', 'ALTER TABLE `characters` ADD INDEX `idx_cached_clan_id` (`ClanID`)');
PREPARE rkern_owner_index_stmt FROM @rkern_owner_index_ddl;
EXECUTE rkern_owner_index_stmt;
DEALLOCATE PREPARE rkern_owner_index_stmt;

SET @rkern_owner_index_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clan_data' AND INDEX_TYPE = 'BTREE'
     GROUP BY INDEX_NAME
    HAVING SUM(CASE WHEN SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'clan_name'
                         AND SUB_PART IS NULL THEN 1 ELSE 0 END) = 1
), 'DO 0', 'ALTER TABLE `clan_data` ADD INDEX `idx_cached_clan_name` (`clan_name`)');
PREPARE rkern_owner_index_stmt FROM @rkern_owner_index_ddl;
EXECUTE rkern_owner_index_stmt;
DEALLOCATE PREPARE rkern_owner_index_stmt;

SET @rkern_owner_index_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clan_data' AND INDEX_TYPE = 'BTREE'
     GROUP BY INDEX_NAME
    HAVING SUM(CASE WHEN SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'leader_id'
                         AND SUB_PART IS NULL THEN 1 ELSE 0 END) = 1
), 'DO 0', 'ALTER TABLE `clan_data` ADD INDEX `idx_cached_clan_leader` (`leader_id`)');
PREPARE rkern_owner_index_stmt FROM @rkern_owner_index_ddl;
EXECUTE rkern_owner_index_stmt;
DEALLOCATE PREPARE rkern_owner_index_stmt;

SET @rkern_owner_index_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'letter' AND INDEX_TYPE = 'BTREE'
     GROUP BY INDEX_NAME
    HAVING SUM(CASE WHEN SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'receiver'
                         AND SUB_PART IS NULL THEN 1 ELSE 0 END) = 1
       AND SUM(CASE WHEN SEQ_IN_INDEX = 2 AND COLUMN_NAME = 'template_id'
                         AND SUB_PART IS NULL THEN 1 ELSE 0 END) = 1
       AND SUM(CASE WHEN SEQ_IN_INDEX = 3 AND COLUMN_NAME = 'date'
                         AND SUB_PART IS NULL THEN 1 ELSE 0 END) = 1
), 'DO 0', 'ALTER TABLE `letter` ADD INDEX `idx_cached_letter_page` (`receiver`, `template_id`, `date`)');
PREPARE rkern_owner_index_stmt FROM @rkern_owner_index_ddl;
EXECUTE rkern_owner_index_stmt;
DEALLOCATE PREPARE rkern_owner_index_stmt;

SET @rkern_owner_index_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'letter' AND INDEX_TYPE = 'BTREE'
     GROUP BY INDEX_NAME
    HAVING SUM(CASE WHEN SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'receiver'
                         AND SUB_PART IS NULL THEN 1 ELSE 0 END) = 1
       AND SUM(CASE WHEN SEQ_IN_INDEX = 2 AND COLUMN_NAME = 'isCheck'
                         AND SUB_PART IS NULL THEN 1 ELSE 0 END) = 1
), 'DO 0', 'ALTER TABLE `letter` ADD INDEX `idx_cached_letter_unread` (`receiver`, `isCheck`)');
PREPARE rkern_owner_index_stmt FROM @rkern_owner_index_ddl;
EXECUTE rkern_owner_index_stmt;
DEALLOCATE PREPARE rkern_owner_index_stmt;

SET @rkern_owner_index_ddl = NULL;

-- ===== 06. Net_board_posts.sql (blob 970dee5f31475666c0dd3d3e2cda0a8ec100cbc6) =====
-- Select the Net schema and stop RKern before applying this one-time import.
-- Existing consolidated boards are authoritative, including empty categories.
-- Leave the legacy sources available for backup or explicit reconciliation.
DELIMITER $$
DROP PROCEDURE IF EXISTS `rkern_import_net_board_posts_v1`$$
CREATE PROCEDURE `rkern_import_net_board_posts_v1`()
SQL SECURITY INVOKER
import_boards: BEGIN
    DECLARE import_lock VARCHAR(64);
    DECLARE acquired INT DEFAULT 0;
    DECLARE stage_comment VARCHAR(80) DEFAULT 'RKern board posts migration v1';
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF acquired = 1 THEN
            DO RELEASE_LOCK(import_lock);
        END IF;
        RESIGNAL;
    END;

    SET import_lock = CONCAT('rkern-net-board-', SHA1(DATABASE()));
    SELECT GET_LOCK(import_lock, 10) INTO acquired;
    IF COALESCE(acquired, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Could not acquire the RKern board import lock';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'board_posts') THEN
        DO RELEASE_LOCK(import_lock);
        LEAVE import_boards;
    END IF;

    CREATE TABLE IF NOT EXISTS `rkern_board_posts_import_v1` (
        `board_id` SMALLINT UNSIGNED NOT NULL,
        `id` INT NOT NULL,
        `name` VARCHAR(255) NULL,
        `date` VARCHAR(255) NULL,
        `title` TEXT NULL,
        `content` MEDIUMTEXT NULL,
        `type` INT NULL,
        PRIMARY KEY (`board_id`, `id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RKern board posts migration v1';

    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'rkern_board_posts_import_v1'
                     AND TABLE_TYPE = 'BASE TABLE' AND ENGINE = 'InnoDB'
                     AND TABLE_COMMENT = stage_comment) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'The RKern board import stage name is already in use';
    END IF;

    START TRANSACTION;
    DELETE FROM `rkern_board_posts_import_v1`;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'board_free' AND TABLE_TYPE = 'BASE TABLE') THEN
        INSERT INTO `rkern_board_posts_import_v1` (`board_id`, `id`, `name`, `date`, `title`, `content`)
        SELECT 1, `id`, `name`, `date`, `title`, `content` FROM `board_free`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'board_notice' AND TABLE_TYPE = 'BASE TABLE') THEN
        INSERT INTO `rkern_board_posts_import_v1` (`board_id`, `id`, `name`, `date`, `title`, `content`)
        SELECT 2, `id`, `name`, `date`, `title`, `content` FROM `board_notice`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'board_notice1' AND TABLE_TYPE = 'BASE TABLE') THEN
        INSERT INTO `rkern_board_posts_import_v1` (`board_id`, `id`, `name`, `date`, `title`, `content`)
        SELECT 3, `id`, `name`, `date`, `title`, `content` FROM `board_notice1`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'board_notice2' AND TABLE_TYPE = 'BASE TABLE') THEN
        INSERT INTO `rkern_board_posts_import_v1` (`board_id`, `id`, `name`, `date`, `title`, `content`)
        SELECT 4, `id`, `name`, `date`, `title`, `content` FROM `board_notice2`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'board_notice3' AND TABLE_TYPE = 'BASE TABLE') THEN
        INSERT INTO `rkern_board_posts_import_v1` (`board_id`, `id`, `name`, `date`, `title`, `content`)
        SELECT 5, `id`, `name`, `date`, `title`, `content` FROM `board_notice3`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'board_posts_fix' AND TABLE_TYPE = 'BASE TABLE') THEN
        INSERT INTO `rkern_board_posts_import_v1` (`board_id`, `id`, `name`, `date`, `title`, `content`)
        SELECT 6, `id`, `name`, `date`, `title`, `content` FROM `board_posts_fix`;
    END IF;
    -- Board 7 belonged to the retired LFC game mode.
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'board_mjnotice' AND TABLE_TYPE = 'BASE TABLE') THEN
        IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'board_mjnotice' AND COLUMN_NAME = 'type') THEN
            INSERT INTO `rkern_board_posts_import_v1` (`board_id`, `id`, `name`, `date`, `title`, `content`, `type`)
            SELECT 8, `id`, `name`, `date`, `title`, `content`, `type` FROM `board_mjnotice`;
        ELSE
            INSERT INTO `rkern_board_posts_import_v1` (`board_id`, `id`, `name`, `date`, `title`, `content`)
            SELECT 8, `id`, `name`, `date`, `title`, `content` FROM `board_mjnotice`;
        END IF;
    END IF;
    COMMIT;

    -- Publish only after all copies succeed. An interrupted run can rebuild
    -- the marked stage. A concurrent live table is never replaced.
    RENAME TABLE `rkern_board_posts_import_v1` TO `board_posts`;
    DO RELEASE_LOCK(import_lock);
END$$
CALL `rkern_import_net_board_posts_v1`()$$
DROP PROCEDURE `rkern_import_net_board_posts_v1`$$
DELIMITER ;

-- ===== 07. Net_runtime_tables.sql (blob 77503fe4749f44aa2c181d77f5dea610f52ef32e) =====
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

-- ===== 08. Net_transaction_engines.sql (blob 9521d5f8b5f149a0b94aad81685956c704293b3f) =====
-- Net 6.9. These tables participate in CacheD transactions. MyISAM cannot
-- roll back their writes. An already converted table is left in place.
-- Engine conversion rebuilds the table. Execute during maintenance with
-- sufficient free space for the source and rebuilt tables. Select the game DB.

SET @net_engine_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLES
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='item_key_boss' AND ENGINE='InnoDB'
), 'DO 0', 'ALTER TABLE `item_key_boss` ENGINE=InnoDB ROW_FORMAT=COMPACT');
PREPARE net_engine_stmt FROM @net_engine_sql;
EXECUTE net_engine_stmt;
DEALLOCATE PREPARE net_engine_stmt;

SET @net_engine_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLES
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='spawnlist_furniture' AND ENGINE='InnoDB'
), 'DO 0', 'ALTER TABLE `spawnlist_furniture` ENGINE=InnoDB ROW_FORMAT=COMPACT');
PREPARE net_engine_stmt FROM @net_engine_sql;
EXECUTE net_engine_stmt;
DEALLOCATE PREPARE net_engine_stmt;

SET @net_engine_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.TABLES
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='town' AND ENGINE='InnoDB'
), 'DO 0', 'ALTER TABLE `town` ENGINE=InnoDB ROW_FORMAT=COMPACT');
PREPARE net_engine_stmt FROM @net_engine_sql;
EXECUTE net_engine_stmt;
DEALLOCATE PREPARE net_engine_stmt;

SET @net_engine_sql = NULL;

-- ===== 09. Net_lookup_indexes.sql (blob 69640823d79e3cd79be53ae37e260b448c02c63f) =====
-- Net 6.9 / MySQL 5.7 and MariaDB. Run once per selected game schema.
-- For TAM, InnoDB also appends the primary key to an owner-only secondary
-- index. A compound index with a different intervening column cannot serve FIFO.

SET @net_index_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tam' AND INDEX_TYPE='BTREE'
     GROUP BY INDEX_NAME
    HAVING MAX(CASE WHEN SEQ_IN_INDEX=1 AND COLUMN_NAME='objid' AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1
       AND (MAX(CASE WHEN SEQ_IN_INDEX=2 AND COLUMN_NAME='id' AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1
            OR (COUNT(*)=1 AND EXISTS (
                SELECT 1 FROM information_schema.STATISTICS pk
                JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=pk.TABLE_SCHEMA AND t.TABLE_NAME=pk.TABLE_NAME
                 WHERE pk.TABLE_SCHEMA=DATABASE() AND pk.TABLE_NAME='tam' AND pk.INDEX_NAME='PRIMARY'
                   AND pk.SEQ_IN_INDEX=1 AND pk.COLUMN_NAME='id' AND t.ENGINE='InnoDB')))
), 'DO 0', 'ALTER TABLE `tam` ADD INDEX `idx_net_tam_owner_fifo` (`objid`, `id`)');
PREPARE net_index_stmt FROM @net_index_sql;
EXECUTE net_index_stmt;
DEALLOCATE PREPARE net_index_stmt;

SET @net_index_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tam' AND INDEX_TYPE='BTREE'
     GROUP BY INDEX_NAME
    HAVING MAX(CASE WHEN SEQ_IN_INDEX=1 AND COLUMN_NAME='encobjid' AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1
       AND (MAX(CASE WHEN SEQ_IN_INDEX=2 AND COLUMN_NAME='id' AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1
            OR (COUNT(*)=1 AND EXISTS (
                SELECT 1 FROM information_schema.STATISTICS pk
                JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=pk.TABLE_SCHEMA AND t.TABLE_NAME=pk.TABLE_NAME
                 WHERE pk.TABLE_SCHEMA=DATABASE() AND pk.TABLE_NAME='tam' AND pk.INDEX_NAME='PRIMARY'
                   AND pk.SEQ_IN_INDEX=1 AND pk.COLUMN_NAME='id' AND t.ENGINE='InnoDB')))
), 'DO 0', 'ALTER TABLE `tam` ADD INDEX `idx_net_tam_reservation_fifo` (`encobjid`, `id`)');
PREPARE net_index_stmt FROM @net_index_sql;
EXECUTE net_index_stmt;
DEALLOCATE PREPARE net_index_stmt;

SET @net_index_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='accounts' AND INDEX_TYPE='BTREE'
     GROUP BY INDEX_NAME
    HAVING MAX(CASE WHEN SEQ_IN_INDEX=1 AND COLUMN_NAME='ip' AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1
), 'DO 0', 'ALTER TABLE `accounts` ADD INDEX `idx_net_accounts_ip` (`ip`)');
PREPARE net_index_stmt FROM @net_index_sql;
EXECUTE net_index_stmt;
DEALLOCATE PREPARE net_index_stmt;

SET @net_index_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='item_key_boss' AND INDEX_TYPE='BTREE'
     GROUP BY INDEX_NAME
    HAVING MAX(CASE WHEN SEQ_IN_INDEX=1 AND COLUMN_NAME='key_id' AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1
), 'DO 0', 'ALTER TABLE `item_key_boss` ADD INDEX `idx_net_room_key` (`key_id`)');
PREPARE net_index_stmt FROM @net_index_sql;
EXECUTE net_index_stmt;
DEALLOCATE PREPARE net_index_stmt;

SET @net_index_sql = NULL;

-- ===== 10. Net_boss_respawn.sql (blob db017467752477e2f2634c22344a857309cd8929) =====
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

-- ===== 11. Net_town_lookup_index.sql (blob 3208763b64ffd25a05ae678a291f8b46f2a79e2d) =====
-- Run on the selected Net schema. The compound key serves residence ranges
-- and highest contribution lookups. Net_transaction_engines.sql converts town to InnoDB.
SET @net_town_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='characters' AND INDEX_TYPE='BTREE'
     GROUP BY INDEX_NAME
    HAVING MAX(CASE WHEN SEQ_IN_INDEX=1 AND COLUMN_NAME='HomeTownID' AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1
       AND MAX(CASE WHEN SEQ_IN_INDEX=2 AND COLUMN_NAME='Contribution' AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1
       AND (MAX(CASE WHEN SEQ_IN_INDEX=3 AND COLUMN_NAME='objid' AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1
            OR COUNT(*)=2)
), 'DO 0', 'ALTER TABLE characters ADD INDEX idx_net_town_contribution (HomeTownID, Contribution, objid)');
PREPARE net_town_stmt FROM @net_town_sql;
EXECUTE net_town_stmt;
DEALLOCATE PREPARE net_town_stmt;
SET @net_town_sql = NULL;

-- ===== 12. PSS_Settings_Item_900000901.sql (blob 313e0f6bfd9d6a1e3ab8bcfc91d56fb13e2c9cf3) =====
-- Native PSS settings opener. Reusable; no item is consumed by the use handler.
-- Apply after backing up your DB, then restart/reload the existing ItemTable.
SET NAMES utf8mb4;

INSERT INTO etcitem
    (item_id, name, name_id, item_type, use_type, material, weight,
     invgfx, grdgfx, itemdesc_id, stackable, max_charge_count, hit, dmg,
     min_lvl, max_lvl, locx, locy, mapid, bless, trade, cant_delete,
     delay_id, delay_time, delay_effect, food_volume, save_at_once, isEndedTimeMessage)
SELECT
    900000901, 'PSS 설정서', 'PSS 설정서', 'other', 'normal', 'paper', 0,
    474, 22, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 1, 1, 0,
    0, 0, 0, 0, 1, 'false'
WHERE NOT EXISTS (SELECT 1 FROM etcitem WHERE item_id = 900000901)
  AND NOT EXISTS (SELECT 1 FROM weapon WHERE item_id = 900000901)
  AND NOT EXISTS (SELECT 1 FROM armor WHERE item_id = 900000901);

-- The existing row is never overwritten. The name must match the source constant.
SELECT item_id, name, use_type, invgfx FROM etcitem WHERE item_id = 900000901;

-- ===== 13. Play_Supporter_Item_900000901.sql (blob 1c5c170f3da4355732e28231fb27e8d4791bfb5a) =====
SET NAMES utf8mb4;

-- Update only this settings item. Its use handler now matches the ID, not its name.
UPDATE etcitem
SET name = 'Play Supporter', name_id = 'Play Supporter', use_type = 'normal'
WHERE item_id = 900000901;

SELECT item_id, name, name_id, use_type FROM etcitem WHERE item_id = 900000901;

-- ===== 14. Attendance_Atomic_Reset_20261129.sql (blob 3cf9c867449b8aef1ddcfb3be5845f2b3f7139a7) =====
-- Normalize existing legacy tables before installing the atomic attendance reset.
-- The server initializes attendance_startup row 2 as the reset generation.
-- Row 1 retains its original daily calendar role.
ALTER TABLE attendance_startup ENGINE=InnoDB;
ALTER TABLE attendance_userinfo ENGINE=InnoDB;

-- ===== 15. Net_retired_empty_tables.sql (blob 1df154745c33ed0071cef6af59c8bc93871bca12) =====
-- Run with the server, background jobs and external writers stopped.
-- Allow-list of removed game features. Check actual rows on the selected
-- schema and keep populated tables, tables with triggers, and FK dependencies.
-- Historical posts and old pet data are handled separately from active data.
SET @net_old_concat_length = @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 32768;
SET @net_drop_names = NULL;
SELECT GROUP_CONCAT(
    CONCAT('SELECT ', QUOTE(t.TABLE_NAME), ' AS table_name FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `',
           t.TABLE_NAME, '` LIMIT 1)')
    ORDER BY t.TABLE_NAME SEPARATOR ' UNION ALL ')
  INTO @net_retired_checks
  FROM information_schema.TABLES t
 WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_TYPE = 'BASE TABLE'
   AND t.TABLE_NAME IN (
       'board_item','board_mjlfc','character_balance','character_blocks','clan_warehousehistory',
       'event_boss_time','ncoin_give_monster','resolvent1','pets','petitem','pettypes',
       'tb_lfccompensate','tb_lfctypes','소지시템드랍','시간제아이템1','시간제아이템2',
       '시간제아이템3','착용시템드랍','특정몹설정','특화사냥터')
   AND NOT EXISTS (
       SELECT 1 FROM information_schema.TRIGGERS tr
        WHERE tr.EVENT_OBJECT_SCHEMA=t.TABLE_SCHEMA AND tr.EVENT_OBJECT_TABLE=t.TABLE_NAME)
   AND NOT EXISTS (
       SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k
        WHERE k.REFERENCED_TABLE_NAME IS NOT NULL
          AND ((k.REFERENCED_TABLE_SCHEMA=t.TABLE_SCHEMA AND k.REFERENCED_TABLE_NAME=t.TABLE_NAME)
            OR (k.TABLE_SCHEMA=t.TABLE_SCHEMA AND k.TABLE_NAME=t.TABLE_NAME)));
SET @net_cleanup_sql = IF(@net_retired_checks IS NULL, 'DO 0', CONCAT(
    'SELECT GROUP_CONCAT(CONCAT(CHAR(96), table_name, CHAR(96)) ORDER BY table_name) INTO @net_drop_names FROM (',
    @net_retired_checks, ') r'));
PREPARE net_cleanup_stmt FROM @net_cleanup_sql;
EXECUTE net_cleanup_stmt;
DEALLOCATE PREPARE net_cleanup_stmt;

SET @net_cleanup_sql = IF(@net_drop_names IS NULL, 'DO 0', CONCAT('DROP TABLE ', @net_drop_names));
PREPARE net_cleanup_stmt FROM @net_cleanup_sql;
EXECUTE net_cleanup_stmt;
DEALLOCATE PREPARE net_cleanup_stmt;
SET SESSION group_concat_max_len = @net_old_concat_length;
SET @net_cleanup_sql=NULL, @net_drop_names=NULL, @net_retired_checks=NULL, @net_old_concat_length=NULL;

-- ===== 16. Remove_Legacy_Pet_Tables_20261126.sql (blob 111f9c64a10fe6e56cea70d7729aab93ba508db6) =====
-- Retire only the legacy pet database tables (RKern / L1J).
-- Stop the game server and CacheD, back up the database, deploy the matching
-- source revision and select the correct game database before executing.
-- MySQL DROP TABLE commits implicitly. Do not disable foreign-key checks.
-- Existing character items and companion records are intentionally untouched.
DROP TABLE IF EXISTS `pets`;
DROP TABLE IF EXISTS `petitem`;
DROP TABLE IF EXISTS `pettypes`;

-- ===== 17. Remove_Legacy_TimeMap_20261129.sql (blob 24372dd5ddb0de9dbd3f3d4a5efeee0b8fa83bd8) =====
-- Optional cleanup after deploying the server without the legacy time map.
-- Run on a backed-up database after checking external consumers of npc.count_map.
SET @rkern_remove_legacy_map = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'npc' AND COLUMN_NAME = 'count_map'
    ),
    'ALTER TABLE npc DROP COLUMN count_map',
    'SELECT 1'
);
PREPARE rkern_remove_legacy_map FROM @rkern_remove_legacy_map;
EXECUTE rkern_remove_legacy_map;
DEALLOCATE PREPARE rkern_remove_legacy_map;

-- End of consolidated upgrade.
