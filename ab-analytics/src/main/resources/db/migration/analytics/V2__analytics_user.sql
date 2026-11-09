-- Facts and the daily rollup gain an owner so charts can be scoped to one user or to a family (a set of users).
-- Both tables are projections: they are dropped and refilled by the startup rebuild (ProjectionService.REVISION).
DROP TABLE ana_price_daily;
DROP TABLE ana_price_fact;
UPDATE ana_meta SET v = 0 WHERE k = 'projection_rev';

CREATE TABLE ana_price_fact (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    receipt_id     INTEGER NOT NULL,
    user_id        INTEGER NOT NULL,
    purchased_date TEXT    NOT NULL,
    currency       TEXT    NOT NULL,
    merchant_id    INTEGER NOT NULL,
    product_id     INTEGER NOT NULL,
    quantity       REAL    NOT NULL,
    unit_minor     REAL    NOT NULL,
    amount_minor   INTEGER NOT NULL
);
CREATE INDEX idx_ana_fact_receipt ON ana_price_fact (receipt_id);
CREATE INDEX idx_ana_fact_bucket ON ana_price_fact (user_id, purchased_date, merchant_id, product_id);

CREATE TABLE ana_price_daily (
    user_id        INTEGER NOT NULL,
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
    PRIMARY KEY (user_id, product_id, merchant_id, currency, purchased_date)
) WITHOUT ROWID;
CREATE INDEX idx_ana_daily_date ON ana_price_daily (user_id, currency, purchased_date);
CREATE INDEX idx_ana_daily_merchant ON ana_price_daily (user_id, merchant_id, purchased_date);
