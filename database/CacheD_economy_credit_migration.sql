-- Apply on the validation database before enabling durable auction refunds.
-- All tables participating in an economy receipt must use InnoDB.
-- Missing/nontransactional ledger causes the payer transaction to fail before
-- any fee or item change. Never truncate this table to clear an error.
CREATE TABLE IF NOT EXISTS character_pending_credits (
  credit_id CHAR(36) NOT NULL,
  char_id INT NOT NULL,
  amount INT NOT NULL,
  reason VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (credit_id),
  KEY pending_credit_owner (char_id),
  CONSTRAINT pending_credit_positive CHECK (amount > 0)
) ENGINE=InnoDB;
