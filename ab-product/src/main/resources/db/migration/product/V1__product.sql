CREATE TABLE prd_product (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT    NOT NULL,
    normalized_name TEXT    NOT NULL,
    brand           TEXT,
    brand_key       TEXT    NOT NULL DEFAULT '',
    category        TEXT    NOT NULL DEFAULT 'OTHER',
    spec_text       TEXT,
    spec_key        TEXT    NOT NULL DEFAULT '',
    spec_value      REAL,
    spec_unit       TEXT,
    spec_dim        TEXT,
    spec_base_qty   REAL,
    barcode         TEXT,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL
);

CREATE UNIQUE INDEX uk_prd_product_identity ON prd_product (normalized_name, brand_key, spec_key);
CREATE INDEX ix_prd_product_brand ON prd_product (brand);
CREATE INDEX ix_prd_product_category ON prd_product (category);
CREATE INDEX ix_prd_product_barcode ON prd_product (barcode);

CREATE TABLE prd_product_tag (
    product_id INTEGER NOT NULL REFERENCES prd_product (id) ON DELETE CASCADE,
    tag        TEXT    NOT NULL,
    PRIMARY KEY (product_id, tag)
) WITHOUT ROWID;

CREATE INDEX ix_prd_product_tag_tag ON prd_product_tag (tag, product_id);
