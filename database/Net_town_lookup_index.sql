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
