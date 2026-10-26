-- Run on a TEST DATABASE first, then deploy to the game DB during maintenance.
-- InnoDB is mandatory: receipt and all affected item tables MUST be transactional.
CREATE TABLE IF NOT EXISTS cached_transaction_receipts (
    tx_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kind VARCHAR(16) NOT NULL,
    owner_a INT NOT NULL,
    owner_b INT NOT NULL,
    committed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tx_id),
    KEY idx_receipt_owner (owner_a, committed_at)
) ENGINE=InnoDB;

-- Every table named below must exist and use ENGINE=InnoDB.
-- Inspect the output; migrate a non-InnoDB table separately with a tested backup.
SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN
 ('cached_transaction_receipts', 'character_items', 'character_warehouse',
  'clan_warehouse', 'character_elf_warehouse',
  'character_special_warehouse', 'character_supplementary_service');
