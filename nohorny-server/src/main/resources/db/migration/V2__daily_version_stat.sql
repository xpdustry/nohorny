-- The requests per day and plugin version, see DailyVersionStat
CREATE TABLE daily_version_stat
(
    day     TEXT    NOT NULL,
    version TEXT    NOT NULL,
    count   INTEGER NOT NULL,
    PRIMARY KEY (day, version)
) WITHOUT ROWID;

-- Counts the retained requests, the deleted ones are lost. Like RequestService#record, the versions past 32
-- characters are left out
INSERT INTO daily_version_stat (day, version, count)
SELECT date(created_at / 1000, 'unixepoch'), version, COUNT(*)
FROM request
WHERE version IS NOT NULL AND length(version) BETWEEN 1 AND 32
GROUP BY 1, 2;
