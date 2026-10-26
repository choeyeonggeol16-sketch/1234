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
