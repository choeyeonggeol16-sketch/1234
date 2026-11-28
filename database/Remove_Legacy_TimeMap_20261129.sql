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
