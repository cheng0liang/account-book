-- Read-side projections of receipts (star schema). Rebuildable from the receipt module at any time.
-- No FK to other modules' tables: data arrives through domain events / ReceiptApi.

CREATE TABLE ana_meta (
    k TEXT PRIMARY KEY,
    v INTEGER NOT NULL
);
INSERT INTO ana_meta (k, v) VALUES ('version', 0), ('projection_rev', 0);

CREATE TABLE ana_merchant_dim (
    id       INTEGER PRIMARY KEY,
    name     TEXT NOT NULL,
    country  TEXT,
    city     TEXT,
    district TEXT,
    type     TEXT
);

CREATE TABLE ana_product_dim (
    id            INTEGER PRIMARY KEY,
    name          TEXT NOT NULL,
    brand         TEXT,
    category      TEXT,
    spec_dim      TEXT,
    spec_base_qty REAL
);

CREATE TABLE ana_product_tag (
    product_id INTEGER NOT NULL,
    tag        TEXT NOT NULL,
    PRIMARY KEY (product_id, tag)
) WITHOUT ROWID;
CREATE INDEX idx_ana_tag ON ana_product_tag (tag, product_id);

-- One row per receipt line. unit_minor = net amount / quantity (what was actually paid per unit).
CREATE TABLE ana_price_fact (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    receipt_id     INTEGER NOT NULL,
    purchased_date TEXT    NOT NULL,
    currency       TEXT    NOT NULL,
    merchant_id    INTEGER NOT NULL,
    product_id     INTEGER NOT NULL,
    quantity       REAL    NOT NULL,
    unit_minor     REAL    NOT NULL,
    amount_minor   INTEGER NOT NULL
);
CREATE INDEX idx_ana_fact_receipt ON ana_price_fact (receipt_id);
CREATE INDEX idx_ana_fact_bucket ON ana_price_fact (purchased_date, merchant_id, product_id);

-- Daily rollup: every chart query reads this (hundreds of rows) instead of scanning facts.
CREATE TABLE ana_price_daily (
    product_id     INTEGER NOT NULL,
    merchant_id    INTEGER NOT NULL,
    currency       TEXT    NOT NULL,
    purchased_date TEXT    NOT NULL,
    n              INTEGER NOT NULL,
    qty            REAL    NOT NULL,
    amount_minor   INTEGER NOT NULL,
    min_unit       REAL    NOT NULL,
    max_unit       REAL    NOT NULL,
    sum_unit       REAL    NOT NULL,
    PRIMARY KEY (product_id, merchant_id, currency, purchased_date)
) WITHOUT ROWID;
CREATE INDEX idx_ana_daily_date ON ana_price_daily (currency, purchased_date);
CREATE INDEX idx_ana_daily_merchant ON ana_price_daily (merchant_id, purchased_date);
