CREATE TABLE IF NOT EXISTS user (
    username            TEXT PRIMARY KEY,
    password_hash       TEXT NOT NULL,
    admin               INTEGER NOT NULL,
    created_at          TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS classification_request (
    id                  INTEGER
        PRIMARY KEY AUTOINCREMENT,
    created_at          TIMESTAMP NOT NULL,
    duration_millis     INTEGER NOT NULL,
    classifier          TEXT NOT NULL,
    rating              TEXT,
    confidence          REAL,
    successful          INTEGER NOT NULL,
    error               TEXT,
    username            TEXT,
    remote_address      TEXT NOT NULL,
    image_media_type    TEXT NOT NULL,
    image               BLOB NOT NULL
);

CREATE INDEX IF NOT EXISTS classification_request_created_at_idx
    ON classification_request(created_at DESC);
