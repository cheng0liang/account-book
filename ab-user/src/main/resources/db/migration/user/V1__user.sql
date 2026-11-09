-- Users, login methods, login locations and sessions. No FK to other modules' tables.

CREATE TABLE usr_user (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,   -- the public user id
    nickname      TEXT    NOT NULL,
    phone         TEXT,
    gender        TEXT    NOT NULL DEFAULT 'UNKNOWN',  -- UNKNOWN | MALE | FEMALE | OTHER
    address       TEXT,
    birth_date    TEXT,                                -- yyyy-MM-dd
    country       TEXT,                                -- ISO 3166-1 alpha-2
    avatar_url    TEXT,
    status        TEXT    NOT NULL DEFAULT 'ACTIVE',
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    last_login_at INTEGER
);
CREATE UNIQUE INDEX uq_usr_user_phone ON usr_user (phone) WHERE phone IS NOT NULL;

-- One row per login: where the device was and when it was located.
CREATE TABLE usr_login_location (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES usr_user (id) ON DELETE CASCADE,
    provider    TEXT    NOT NULL,                      -- login method: PHONE | APPLE | GOOGLE | WECHAT | QQ | LINE
    latitude    REAL    NOT NULL,
    longitude   REAL    NOT NULL,
    accuracy_m  REAL,
    located_at  INTEGER NOT NULL,                      -- when the position was fixed (UTC epoch ms)
    ip          TEXT,
    user_agent  TEXT,
    created_at  INTEGER NOT NULL
);
CREATE INDEX idx_usr_loc_user ON usr_login_location (user_id, located_at DESC);

-- Login identities. A user may have several (phone + Apple + ...); (provider, provider_uid) is globally unique.
-- Provider access/refresh tokens are deliberately not stored: they are only used once to read the profile.
CREATE TABLE usr_auth_account (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL REFERENCES usr_user (id) ON DELETE CASCADE,
    provider      TEXT    NOT NULL,
    provider_uid  TEXT    NOT NULL,
    union_id      TEXT,
    nickname      TEXT,
    email         TEXT,
    avatar_url    TEXT,
    raw_profile   TEXT,
    created_at    INTEGER NOT NULL,
    last_login_at INTEGER NOT NULL,
    UNIQUE (provider, provider_uid)
);
CREATE INDEX idx_usr_auth_user ON usr_auth_account (user_id);

-- Opaque bearer sessions; only the SHA-256 of the token is stored.
CREATE TABLE usr_session (
    token_hash   TEXT    PRIMARY KEY,
    user_id      INTEGER NOT NULL REFERENCES usr_user (id) ON DELETE CASCADE,
    provider     TEXT    NOT NULL,
    user_agent   TEXT,
    created_at   INTEGER NOT NULL,
    expires_at   INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
) WITHOUT ROWID;
CREATE INDEX idx_usr_session_user ON usr_session (user_id);

CREATE TABLE usr_sms_code (
    phone     TEXT    PRIMARY KEY,
    code_hash TEXT    NOT NULL,
    attempts  INTEGER NOT NULL DEFAULT 0,
    sent_at   INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
) WITHOUT ROWID;

CREATE TABLE usr_oauth_state (
    state        TEXT    PRIMARY KEY,
    provider     TEXT    NOT NULL,
    bind_user_id INTEGER,
    created_at   INTEGER NOT NULL
) WITHOUT ROWID;

-- One-time, short-lived hand-off from the OAuth callback redirect to the SPA (which then adds the location).
CREATE TABLE usr_login_ticket (
    ticket_hash TEXT    PRIMARY KEY,
    user_id     INTEGER NOT NULL,
    provider    TEXT    NOT NULL,
    expires_at  INTEGER NOT NULL
) WITHOUT ROWID;
