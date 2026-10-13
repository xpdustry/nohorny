-- Renames the PURGED image state to DELETED, the wording of the pages.
--
-- SQLite cannot alter a CHECK constraint, so request is rebuilt. request_step is rebuilt with it, dropping the old
-- request would otherwise delete the steps through their foreign key.

CREATE TABLE request_new
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
    -- Who sent it: the username of a USER, the network of a MINDUSTRY_NETWORK if listed with one, nothing otherwise.
    -- Kept as text, the requests outlive the accounts
    requester_type   TEXT    NOT NULL CHECK (requester_type IN ('USER', 'MINDUSTRY_NETWORK', 'LOCALHOST', 'ANONYMOUS')),
    requester_name   TEXT CHECK (CASE requester_type
                                     WHEN 'USER' THEN requester_name IS NOT NULL
                                     WHEN 'LOCALHOST' THEN requester_name IS NULL
                                     WHEN 'ANONYMOUS' THEN requester_name IS NULL
                                     ELSE 1 END),
    remote_address   TEXT    NOT NULL,
    image_state      TEXT    NOT NULL CHECK (image_state IN ('NONE', 'STORED', 'EXPIRED', 'DELETED')),
    -- The lowercase hex SHA-256 of the JPEG image, the name of its file in the image directory. Identical images share one
    image_hash       TEXT
);

INSERT INTO request_new
SELECT id, created_at, duration_millis, classifier, outcome, confidence, error, version, requester_type,
       requester_name, remote_address,
       CASE WHEN image_state = 'PURGED' THEN 'DELETED' ELSE image_state END,
       image_hash
FROM request;

-- References request_new, renamed to request below along with the reference
CREATE TABLE request_step_new
(
    request_id      TEXT    NOT NULL REFERENCES request_new (id) ON DELETE CASCADE,
    position        INTEGER NOT NULL,
    classifier      TEXT    NOT NULL,
    duration_millis INTEGER NOT NULL,
    outcome         TEXT    NOT NULL CHECK (outcome IN ('SAFE', 'WARN', 'NSFW', 'FAILED')),
    confidence      REAL CHECK ((outcome = 'FAILED') = (confidence IS NULL)),
    error           TEXT CHECK ((outcome = 'FAILED') = (error IS NOT NULL)),
    PRIMARY KEY (request_id, position)
) WITHOUT ROWID;

INSERT INTO request_step_new
SELECT request_id, position, classifier, duration_millis, outcome, confidence, error
FROM request_step;

DROP TABLE request_step;
DROP TABLE request;
ALTER TABLE request_new RENAME TO request;
ALTER TABLE request_step_new RENAME TO request_step;

-- Covers the counts of the last 24 hours
CREATE INDEX request_created_at_idx ON request (created_at, outcome);
-- Covers the filtered pages
CREATE INDEX request_outcome_idx ON request (outcome, id);
-- Finds the requests sharing an image before its file is deleted. SQLite uses it for image_hash = ? despite the filter
CREATE INDEX request_image_hash_idx ON request (image_hash) WHERE image_hash IS NOT NULL;
