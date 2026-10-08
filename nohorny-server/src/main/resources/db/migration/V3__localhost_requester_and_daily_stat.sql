-- Adds the LOCALHOST requester, the clients of the loopback interface without an account, recorded as ANONYMOUS so far,
-- and merges the daily statistics into a single table.
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
    image_state      TEXT    NOT NULL CHECK (image_state IN ('NONE', 'STORED', 'EXPIRED', 'PURGED')),
    -- The lowercase hex SHA-256 of the JPEG image, the name of its file in the image directory. Identical images share one
    image_hash       TEXT
);

-- The loopback addresses as written by the servlet container, see MindustryClientDirectory#whois
INSERT INTO request_new
SELECT id, created_at, duration_millis, classifier, outcome, confidence, error, version,
       CASE
           WHEN requester_type = 'ANONYMOUS'
               AND (remote_address LIKE '127.%' OR remote_address IN ('::1', '0:0:0:0:0:0:0:1'))
               THEN 'LOCALHOST'
           ELSE requester_type END,
       requester_name, remote_address, image_state, image_hash
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

-- The requests per day, bucket, requester type, network and plugin version, kept after the requests themselves are
-- deleted, see DailyStat. Each breakdown of the statistics is a sum over the other columns
CREATE TABLE daily_stat_new
(
    day            TEXT    NOT NULL,
    bucket         TEXT    NOT NULL CHECK (bucket IN ('SAFE', 'WARN', 'NSFW', 'FAILED')),
    -- Empty for the counts migrated without their requests, see below
    requester_type TEXT    NOT NULL CHECK (requester_type IN ('', 'USER', 'MINDUSTRY_NETWORK', 'LOCALHOST', 'ANONYMOUS')),
    -- The network of a MINDUSTRY_NETWORK listed with one, empty otherwise
    network        TEXT    NOT NULL CHECK (network = '' OR requester_type = 'MINDUSTRY_NETWORK'),
    -- Empty without a version or past 32 characters, the clients send it freely
    version        TEXT    NOT NULL CHECK (length(version) <= 32),
    count          INTEGER NOT NULL,
    PRIMARY KEY (day, bucket, requester_type, network, version)
) WITHOUT ROWID;

-- Rebuilds the statistics of the retained requests, like DailyStatRepository#increment
INSERT INTO daily_stat_new (day, bucket, requester_type, network, version, count)
SELECT date(created_at / 1000, 'unixepoch'),
       outcome,
       requester_type,
       CASE WHEN requester_type = 'MINDUSTRY_NETWORK' THEN coalesce(requester_name, '') ELSE '' END,
       CASE WHEN length(version) BETWEEN 1 AND 32 THEN version ELSE '' END,
       COUNT(*)
FROM request
GROUP BY 1, 2, 3, 4, 5;

-- The deleted requests only survive in the old totals per day and bucket, without their requester and version. Their
-- networks and versions are lost, past the 90 days of retention by default so outside the ranges of the charts
INSERT INTO daily_stat_new (day, bucket, requester_type, network, version, count)
SELECT d.day, d.bucket, '', '', '', d.count - coalesce(r.count, 0)
FROM daily_stat d
         LEFT JOIN (SELECT day, bucket, SUM(count) AS count FROM daily_stat_new GROUP BY day, bucket) r
                   ON r.day = d.day AND r.bucket = d.bucket
WHERE d.count > coalesce(r.count, 0);

DROP TABLE daily_stat;
DROP TABLE daily_network_stat;
DROP TABLE daily_version_stat;
ALTER TABLE daily_stat_new RENAME TO daily_stat;
