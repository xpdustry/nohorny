-- Timestamps are epoch milliseconds, days are ISO-8601 dates in UTC.
-- Enumerations are stored by name, mapped as strings by the JPA entities.

CREATE TABLE request
(
    id               TEXT    NOT NULL PRIMARY KEY,
    created_at       INTEGER NOT NULL,
    duration_millis  INTEGER NOT NULL,
    -- The classifier of the final verdict, or the one that failed
    classifier       TEXT    NOT NULL,
    -- The rating of the verdict or FAILED. A verdict has a confidence, a failure the name of its exception.
    -- The same columns are mapped in request_step, see Outcome
    outcome          TEXT    NOT NULL CHECK (outcome IN ('SAFE', 'WARN', 'NSFW', 'FAILED')),
    confidence       REAL CHECK ((outcome = 'FAILED') = (confidence IS NULL)),
    error            TEXT CHECK ((outcome = 'FAILED') = (error IS NOT NULL)),
    version          TEXT,
    username         TEXT,
    remote_address   TEXT    NOT NULL,
    -- The normalized network name of the public Mindustry server that sent the request, if listed
    network          TEXT,
    image_media_type TEXT,
    image_state      TEXT    NOT NULL CHECK (image_state IN ('NONE', 'STORED', 'EXPIRED', 'PURGED')),
    -- The lowercase hex SHA-256 of the image, the name of its file in the image directory. Identical images share one
    image_hash       TEXT
);

-- Covers the counts of the last 24 hours
CREATE INDEX request_created_at_idx ON request (created_at, outcome);
-- Covers the filtered pages
CREATE INDEX request_outcome_idx ON request (outcome, id);
-- Finds the requests sharing an image before its file is deleted. SQLite uses it for image_hash = ? despite the filter
CREATE INDEX request_image_hash_idx ON request (image_hash) WHERE image_hash IS NOT NULL;

CREATE TABLE request_step
(
    request_id      TEXT    NOT NULL REFERENCES request (id) ON DELETE CASCADE,
    position        INTEGER NOT NULL,
    classifier      TEXT    NOT NULL,
    duration_millis INTEGER NOT NULL,
    outcome         TEXT    NOT NULL CHECK (outcome IN ('SAFE', 'WARN', 'NSFW', 'FAILED')),
    confidence      REAL CHECK ((outcome = 'FAILED') = (confidence IS NULL)),
    error           TEXT CHECK ((outcome = 'FAILED') = (error IS NOT NULL)),
    PRIMARY KEY (request_id, position)
) WITHOUT ROWID;

CREATE TABLE daily_stat
(
    day    TEXT    NOT NULL,
    bucket TEXT    NOT NULL CHECK (bucket IN ('SAFE', 'WARN', 'NSFW', 'FAILED')),
    count  INTEGER NOT NULL,
    PRIMARY KEY (day, bucket)
) WITHOUT ROWID;

-- The requests of each listed network per day. The other requests are the difference with daily_stat
CREATE TABLE daily_network_stat
(
    day     TEXT    NOT NULL,
    network TEXT    NOT NULL,
    count   INTEGER NOT NULL,
    PRIMARY KEY (day, network)
) WITHOUT ROWID;

CREATE TABLE user_account
(
    username      TEXT    NOT NULL PRIMARY KEY,
    password_hash TEXT    NOT NULL,
    admin         INTEGER NOT NULL CHECK (admin IN (0, 1)),
    created_at    INTEGER NOT NULL
);
