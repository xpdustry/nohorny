-- Timestamps are epoch milliseconds, days are ISO-8601 dates in UTC.
-- Enumerations are stored by name, mapped as strings by the JPA entities.

CREATE TABLE request
(
    id               TEXT    NOT NULL PRIMARY KEY,
    created_at       INTEGER NOT NULL,
    duration_millis  INTEGER NOT NULL,
    successful       INTEGER NOT NULL CHECK (successful IN (0, 1)),
    rating           TEXT CHECK (rating IN ('SAFE', 'WARN', 'NSFW')),
    confidence       REAL,
    classifier       TEXT    NOT NULL,
    error            TEXT,
    version          TEXT,
    username         TEXT,
    remote_address   TEXT    NOT NULL,
    image_media_type TEXT,
    image_state      TEXT    NOT NULL CHECK (image_state IN ('NONE', 'STORED', 'EXPIRED', 'PURGED')),
    -- Last, so reading the other columns never walks the overflow pages of the image
    image            BLOB
);

-- Covers the counts of the last 24 hours
CREATE INDEX request_created_at_idx ON request (created_at, rating);
CREATE INDEX request_rating_idx ON request (rating, id);

CREATE TABLE request_step
(
    request_id      TEXT    NOT NULL REFERENCES request (id) ON DELETE CASCADE,
    position        INTEGER NOT NULL,
    classifier      TEXT    NOT NULL,
    rating          TEXT CHECK (rating IN ('SAFE', 'WARN', 'NSFW')),
    confidence      REAL,
    duration_millis INTEGER NOT NULL,
    error           TEXT,
    PRIMARY KEY (request_id, position)
) WITHOUT ROWID;

CREATE TABLE daily_stat
(
    day    TEXT    NOT NULL,
    bucket TEXT    NOT NULL CHECK (bucket IN ('SAFE', 'WARN', 'NSFW', 'FAILED')),
    count  INTEGER NOT NULL,
    PRIMARY KEY (day, bucket)
) WITHOUT ROWID;

CREATE TABLE user_account
(
    username      TEXT    NOT NULL PRIMARY KEY,
    password_hash TEXT    NOT NULL,
    admin         INTEGER NOT NULL CHECK (admin IN (0, 1)),
    created_at    INTEGER NOT NULL
);
