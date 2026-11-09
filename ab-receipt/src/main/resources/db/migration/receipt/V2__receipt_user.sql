-- Receipts belong to a user. 0 = legacy rows saved before login existed; the first registered user claims them.
ALTER TABLE rcp_receipt ADD COLUMN user_id INTEGER NOT NULL DEFAULT 0;

CREATE INDEX ix_rcp_receipt_user ON rcp_receipt (user_id, purchased_at);
