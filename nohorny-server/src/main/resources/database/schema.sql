CREATE TABLE IF NOT EXISTS users (
    username            TEXT PRIMARY KEY,
    password_hash       TEXT NOT NULL,
    created_at          TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    username            TEXT NOT NULL,
    role                TEXT NOT NULL
        CHECK (role IN ('ADMIN', 'API')),
    PRIMARY KEY (username, role),
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS classification_requests (
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
    image BLOB          NOT NULL
);

CREATE INDEX IF NOT EXISTS classification_requests_created_at_idx
    ON classification_requests(created_at DESC);
