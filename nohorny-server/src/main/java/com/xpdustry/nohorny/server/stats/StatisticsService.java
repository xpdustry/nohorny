// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import com.xpdustry.nohorny.server.persistence.BucketCount;
import com.xpdustry.nohorny.server.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.server.persistence.DailyStatRepository;
import com.xpdustry.nohorny.server.persistence.NamedCount;
import com.xpdustry.nohorny.server.persistence.RatingBucket;
import com.xpdustry.nohorny.server.persistence.RequesterType;
import com.xpdustry.nohorny.server.persistence.SlotCount;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        final var recent = Statistics.Window.of(
                toMap(this.requests.countByBucketSince(now.minus(Duration.ofHours(Range.H24.slots())))));
        return new Statistics(allTime.total(), allTime.ratings(), recent, now);
    }

    public Networks networks(final Range range) {
        final var counts = this.counts(range, this.requests::countByNetworkSince, this.dailyStats::countByNetworkSince);
        final var first = range.first();
        final var unnamed = range.slot() == Range.Slot.HOUR
                ? this.requests.countByUnnamedRequesterSince(first)
                : this.dailyStats.countByUnnamedRequesterSince(range.slot().idOf(first));
        final var requesters = new ArrayList<Networks.RequesterCount>();
        var unattributed = 0L;
        for (final var count : Counts.of(unnamed, 0).named()) {
            final var type = RequesterType.fromName(count.name());
            if (type.isPresent()) {
                requesters.add(new Networks.RequesterCount(type.get(), count.count()));
            } else {
                unattributed += count.count();
            }
        }
        return new Networks(range, counts.total(), counts.named(), requesters, unattributed);
    }

    public Versions versions(final Range range) {
        final var counts = this.counts(range, this.requests::countByVersionSince, this.dailyStats::countByVersionSince);
        return new Versions(
                range,
                counts.total(),
                counts.named().stream()
                        .sorted(Comparator.comparing(Count::name, VersionComparator.INSTANCE.reversed()))
                        .toList());
    }

    private Counts counts(
            final Range range,
            final Function<Instant, List<NamedCount>> retained,
            final Function<String, List<NamedCount>> daily) {
        final var first = range.first();
        if (range.slot() == Range.Slot.HOUR) {
            final var total = sum(this.requests.countByBucketSince(first).stream()
                    .map(BucketCount::getTotal)
                    .toList());
            return Counts.of(retained.apply(first), total);
        }
        final var day = range.slot().idOf(first);
        final var total = sum(this.dailyStats.countByDaySince(day).stream()
                .map(SlotCount::getTotal)
                .toList());
        return Counts.of(daily.apply(day), total);
    }

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

    public History history(final Range range) {
        final var first = range.first();
        final var slot = range.slot();
        final var counts = bySlot(
                slot == Range.Slot.HOUR
                        ? this.requests.countByHourSince(first)
                        : this.dailyStats.countByDaySince(slot.idOf(first)));
        final var slots = new ArrayList<History.Slot>(range.slots());
        for (int i = 0; i < range.slots(); i++) {
            final var start = first.plus(i, slot.unit());
            slots.add(new History.Slot(start, Statistics.Ratings.of(counts.getOrDefault(slot.idOf(start), Map.of()))));
        }
        return new History(range, slot, slots);
    }

    private static long sum(final List<Long> totals) {
        return totals.stream().mapToLong(Long::longValue).sum();
    }

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
