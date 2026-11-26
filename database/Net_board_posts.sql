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
