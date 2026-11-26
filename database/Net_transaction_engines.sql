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
