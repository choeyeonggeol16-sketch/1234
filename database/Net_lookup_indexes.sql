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
