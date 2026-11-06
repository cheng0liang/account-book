CREATE TABLE mer_merchant (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT    NOT NULL,
    normalized_name TEXT    NOT NULL,
    country         TEXT,
    city            TEXT,
    city_key        TEXT    NOT NULL DEFAULT '',
    district        TEXT,
    type            TEXT    NOT NULL DEFAULT 'OTHER',
    address         TEXT,
    latitude        REAL,
    longitude       REAL,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL
);

CREATE UNIQUE INDEX uk_mer_merchant_name_city ON mer_merchant (normalized_name, city_key);
CREATE INDEX ix_mer_merchant_area ON mer_merchant (country, city, district);
CREATE INDEX ix_mer_merchant_type ON mer_merchant (type);
