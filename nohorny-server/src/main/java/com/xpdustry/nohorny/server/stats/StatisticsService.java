// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import com.xpdustry.nohorny.server.persistence.BucketCount;
import com.xpdustry.nohorny.server.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.server.persistence.DailyStatRepository;
import com.xpdustry.nohorny.server.persistence.NamedCount;
import com.xpdustry.nohorny.server.persistence.RatingBucket;
import com.xpdustry.nohorny.server.persistence.SlotCount;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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

    /// @param range `24h` from the retained requests, `7d`, `30d` or `90d` from the daily statistics,
    ///     over the same windows as [#history]
    /// @return the requests per listed network and from the loopback interface, or empty for an unknown range
    public Optional<Networks> networks(final String range) {
        return this.counts(range, this.requests::countByNetworkSince, this.dailyStats::countByNetworkSince)
                .map(counts -> new Networks(range, counts.total(), counts.named(), this.localhost(range)));
    }

    /// @param range a known range, see [#networks]
    private long localhost(final String range) {
        return range.equals("24h")
                ? this.requests.countLocalhostSince(firstHour())
                : this.dailyStats.countLocalhostSince(firstDay(days(range)).toString());
    }

    /// @param range `24h` from the retained requests, `7d`, `30d` or `90d` from the daily statistics,
    ///     over the same windows as [#history]
    /// @return the requests per plugin version, or empty for an unknown range
    public Optional<Versions> versions(final String range) {
        return this.counts(range, this.requests::countByVersionSince, this.dailyStats::countByVersionSince)
                .map(counts -> new Versions(
                        range,
                        counts.total(),
                        counts.named().stream()
                                .sorted(Comparator.comparing(Count::name, VersionComparator.INSTANCE.reversed()))
                                .toList()));
    }

    /// The named counts of a range, from the retained requests for `24h` and from the daily statistics otherwise,
    /// with every request of the range.
    ///
    /// @return empty for an unknown range
    private Optional<Counts> counts(
            final String range,
            final Function<Instant, List<NamedCount>> retained,
            final Function<String, List<NamedCount>> daily) {
        if (range.equals("24h")) {
            final var first = firstHour();
            final var total = sum(this.requests.countByBucketSince(first).stream()
                    .map(BucketCount::getTotal)
                    .toList());
            return Optional.of(Counts.of(retained.apply(first), total));
        }
        final var days = days(range);
        if (days == 0) {
            return Optional.empty();
        }
        final var first = firstDay(days).toString();
        final var total = sum(this.dailyStats.countByDaySince(first).stream()
                .map(SlotCount::getTotal)
                .toList());
        return Optional.of(Counts.of(daily.apply(first), total));
    }

    /// @param named the counts by name, the highest first then by name
    private record Counts(List<Count> named, long total) {

        static Counts of(final List<NamedCount> counts, final long total) {
            return new Counts(
                    counts.stream()
                            .map(count -> new Count(count.getName(), count.getTotal()))
                            .sorted(Comparator.comparingLong(Count::count)
                                    .reversed()
                                    .thenComparing(Count::name))
                            .toList(),
                    total);
        }
    }

    /// @param range `24h` for hourly slots from the retained requests,
    ///     `7d`, `30d` or `90d` for daily slots from the daily statistics
    /// @return the zero-filled history, or empty for an unknown range
    public Optional<History> history(final String range) {
        if (range.equals("24h")) {
            final var first = firstHour();
            final var counts = bySlot(this.requests.countByHourSince(first));
            final var slots = new ArrayList<History.Slot>(24);
            for (int i = 0; i < 24; i++) {
                final var start = first.plus(Duration.ofHours(i));
                final var hour = String.valueOf(
                        start.toEpochMilli() / Duration.ofHours(1).toMillis());
                slots.add(new History.Slot(start, Statistics.Ratings.of(counts.getOrDefault(hour, Map.of()))));
            }
            return Optional.of(new History(range, "hour", slots));
        }
        final var days = days(range);
        if (days == 0) {
            return Optional.empty();
        }
        final var first = firstDay(days);
        final var counts = bySlot(this.dailyStats.countByDaySince(first.toString()));
        final var slots = new ArrayList<History.Slot>(days);
        for (int i = 0; i < days; i++) {
            final var day = first.plusDays(i);
            slots.add(new History.Slot(
                    day.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    Statistics.Ratings.of(counts.getOrDefault(day.toString(), Map.of()))));
        }
        return Optional.of(new History(range, "day", slots));
    }

    /// The start of the 24 hour window, the current hour is its last slot.
    private static Instant firstHour() {
        return Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(23));
    }

    /// @return the number of days of a daily range, `0` if it is not one
    private static int days(final String range) {
        return switch (range) {
            case "7d" -> 7;
            case "30d" -> 30;
            case "90d" -> 90;
            default -> 0;
        };
    }

    /// The first day of a daily range, today is its last slot.
    private static LocalDate firstDay(final int days) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(days - 1);
    }

    private static long sum(final List<Long> totals) {
        return totals.stream().mapToLong(Long::longValue).sum();
    }

    /// @return the counts per slot then per bucket
    private static Map<String, Map<RatingBucket, Long>> bySlot(final List<SlotCount> counts) {
        final var slots = new HashMap<String, Map<RatingBucket, Long>>();
        for (final var count : counts) {
            slots.computeIfAbsent(count.getSlot(), ignored -> new EnumMap<>(RatingBucket.class))
                    .merge(count.getBucket(), count.getTotal(), Long::sum);
        }
        return slots;
    }

    private static Map<RatingBucket, Long> toMap(final List<BucketCount> counts) {
        final var map = new EnumMap<RatingBucket, Long>(RatingBucket.class);
        for (final var count : counts) {
            map.merge(count.getBucket(), count.getTotal(), Long::sum);
        }
        return map;
    }
}
