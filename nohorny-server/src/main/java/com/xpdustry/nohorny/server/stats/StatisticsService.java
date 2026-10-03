// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import com.xpdustry.nohorny.server.persistence.BucketCount;
import com.xpdustry.nohorny.server.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.server.persistence.DailyStatRepository;
import com.xpdustry.nohorny.server.persistence.RatingBucket;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {

    private final ClassificationRequestRepository requests;
    private final DailyStatRepository dailyStats;

    public StatisticsService(final ClassificationRequestRepository requests, final DailyStatRepository dailyStats) {
        this.requests = requests;
        this.dailyStats = dailyStats;
    }

    public Statistics compute() {
        final var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final var allTime = Statistics.Window.of(toMap(this.dailyStats.countByBucket()));
        final var recent =
                Statistics.Window.of(toMap(this.requests.countByBucketSince(now.minus(Duration.ofHours(24)))));
        return new Statistics(allTime.total(), allTime.ratings(), recent, now);
    }

    private static Map<RatingBucket, Long> toMap(final List<BucketCount> counts) {
        final var map = new EnumMap<RatingBucket, Long>(RatingBucket.class);
        for (final var count : counts) {
            map.merge(count.getBucket(), count.getTotal(), Long::sum);
        }
        return map;
    }
}
