-- Families and their members. user_id refers to usr_user by value only (no cross-module FK).

CREATE TABLE fam_family (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    owner_id    INTEGER NOT NULL,
    invite_code TEXT    NOT NULL,
    created_at  INTEGER NOT NULL
);
CREATE UNIQUE INDEX uq_fam_family_invite ON fam_family (invite_code);

-- user_id is unique: a user belongs to at most one family.
CREATE TABLE fam_member (
    family_id INTEGER NOT NULL REFERENCES fam_family (id) ON DELETE CASCADE,
    user_id   INTEGER NOT NULL UNIQUE,
    role      TEXT    NOT NULL,          -- OWNER | MEMBER
    joined_at INTEGER NOT NULL,
    PRIMARY KEY (family_id, user_id)
);
