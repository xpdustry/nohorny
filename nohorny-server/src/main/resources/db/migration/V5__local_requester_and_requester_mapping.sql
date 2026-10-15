-- Renames the LOCALHOST requester to LOCAL, which now covers the private and link-local addresses too, records whether
-- a request was sent with an account, and adds the requester mappings. The USER requests recorded so far all
-- authenticated, no mapping could give them yet.
--
-- SQLite cannot alter a CHECK constraint, so request and daily_stat are rebuilt. request_step is rebuilt with request,
-- dropping the old request would otherwise delete the steps through their foreign key.

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
    requester_type   TEXT    NOT NULL CHECK (requester_type IN ('USER', 'MINDUSTRY_NETWORK', 'LOCAL', 'ANONYMOUS')),
    requester_name   TEXT CHECK (CASE requester_type
                                     WHEN 'USER' THEN requester_name IS NOT NULL
                                     WHEN 'LOCAL' THEN requester_name IS NULL
                                     WHEN 'ANONYMOUS' THEN requester_name IS NULL
                                     ELSE 1 END),
    -- Whether it was sent with an account. A requester mapping can also give a USER, which it can change again later
    authenticated    INTEGER NOT NULL CHECK (authenticated IN (0, 1) AND (authenticated = 0 OR requester_type = 'USER')),
    remote_address   TEXT    NOT NULL,
    image_state      TEXT    NOT NULL CHECK (image_state IN ('NONE', 'STORED', 'EXPIRED', 'DELETED')),
    -- The lowercase hex SHA-256 of the JPEG image, the name of its file in the image directory. Identical images share one
    image_hash       TEXT
);

INSERT INTO request_new
SELECT id, created_at, duration_millis, classifier, outcome, confidence, error, version,
       CASE WHEN requester_type = 'LOCALHOST' THEN 'LOCAL' ELSE requester_type END,
       requester_name, requester_type = 'USER', remote_address, image_state, image_hash
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
-- Finds the requests of an address when a requester mapping is applied to them
CREATE INDEX request_remote_address_idx ON request (remote_address);

-- The requests per day, bucket, requester type, network and plugin version, kept after the requests themselves are
-- deleted, see DailyStat. Each breakdown of the statistics is a sum over the other columns
CREATE TABLE daily_stat_new
(
    day            TEXT    NOT NULL,
    bucket         TEXT    NOT NULL CHECK (bucket IN ('SAFE', 'WARN', 'NSFW', 'FAILED')),
    -- Empty for the counts migrated without their requests, see V3
    requester_type TEXT    NOT NULL CHECK (requester_type IN ('', 'USER', 'MINDUSTRY_NETWORK', 'LOCAL', 'ANONYMOUS')),
    -- The network of a MINDUSTRY_NETWORK listed with one, empty otherwise
    network        TEXT    NOT NULL CHECK (network = '' OR requester_type = 'MINDUSTRY_NETWORK'),
    -- Empty without a version or past 32 characters, the clients send it freely
    version        TEXT    NOT NULL CHECK (length(version) <= 32),
    count          INTEGER NOT NULL,
    PRIMARY KEY (day, bucket, requester_type, network, version)
) WITHOUT ROWID;

INSERT INTO daily_stat_new (day, bucket, requester_type, network, version, count)
SELECT day, bucket, CASE WHEN requester_type = 'LOCALHOST' THEN 'LOCAL' ELSE requester_type END, network, version, count
FROM daily_stat;

DROP TABLE daily_stat;
ALTER TABLE daily_stat_new RENAME TO daily_stat;

-- Gives the clients of an address range another requester, see RequesterMapping. The most specific range wins
CREATE TABLE requester_mapping
(
    id             TEXT    NOT NULL PRIMARY KEY,
    -- The normalized CIDR notation, such as 172.16.0.0/12, see AddressRange
    address_range  TEXT    NOT NULL UNIQUE,
    -- The requester the clients become, named like in request. A mapped Mindustry network always has a name. The
    -- mappings to an account are deleted with it, see UserService#delete
    requester_type TEXT    NOT NULL CHECK (requester_type IN ('USER', 'MINDUSTRY_NETWORK', 'LOCAL', 'ANONYMOUS')),
    requester_name TEXT CHECK (CASE requester_type
                                   WHEN 'LOCAL' THEN requester_name IS NULL
                                   WHEN 'ANONYMOUS' THEN requester_name IS NULL
                                   ELSE requester_name IS NOT NULL END),
    created_at     INTEGER NOT NULL
);
