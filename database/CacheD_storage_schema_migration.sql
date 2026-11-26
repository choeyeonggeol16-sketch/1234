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
