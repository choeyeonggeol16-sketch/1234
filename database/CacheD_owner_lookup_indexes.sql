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
