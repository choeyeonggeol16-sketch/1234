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
