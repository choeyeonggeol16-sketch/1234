-- Preserve existing character rows and enable the growth-history field used by CacheD.
SET @rkern_growth_ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE characters ADD COLUMN hp_mp_growth TEXT NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'characters'
      AND COLUMN_NAME = 'hp_mp_growth'
);
PREPARE rkern_growth_stmt FROM @rkern_growth_ddl;
EXECUTE rkern_growth_stmt;
DEALLOCATE PREPARE rkern_growth_stmt;
