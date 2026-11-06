CREATE TABLE rcp_receipt (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    merchant_id    INTEGER NOT NULL,
    merchant_name  TEXT    NOT NULL,
    purchased_at   TEXT    NOT NULL,
    purchased_date TEXT    NOT NULL,
    currency       TEXT    NOT NULL,
    total_minor    INTEGER NOT NULL,
    tax_minor      INTEGER,
    discount_minor INTEGER NOT NULL DEFAULT 0,
    payment_method TEXT,
    note           TEXT,
    source         TEXT    NOT NULL DEFAULT 'MANUAL',
    image_path     TEXT,
    item_count     INTEGER NOT NULL,
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL
);

CREATE INDEX ix_rcp_receipt_purchased ON rcp_receipt (purchased_at);
CREATE INDEX ix_rcp_receipt_date ON rcp_receipt (purchased_date);
CREATE INDEX ix_rcp_receipt_merchant ON rcp_receipt (merchant_id, purchased_at);

CREATE TABLE rcp_receipt_item (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    receipt_id       INTEGER NOT NULL REFERENCES rcp_receipt (id) ON DELETE CASCADE,
    line_no          INTEGER NOT NULL,
    product_id       INTEGER NOT NULL,
    product_name     TEXT    NOT NULL,
    quantity         REAL    NOT NULL,
    unit_price_minor INTEGER NOT NULL,
    discount_minor   INTEGER NOT NULL DEFAULT 0,
    amount_minor     INTEGER NOT NULL,
    note             TEXT
);

CREATE INDEX ix_rcp_item_receipt ON rcp_receipt_item (receipt_id, line_no);
CREATE INDEX ix_rcp_item_product ON rcp_receipt_item (product_id);
