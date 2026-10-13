// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.function.Predicate;
import mindustry.Vars;
import mindustry.world.Block;
import org.jspecify.annotations.Nullable;

// Groups the queued buildings a few steps per tick and sends each group to the classifier.
//
// Queued buildings are gathered in regions, a region is only grouped once its builders are done with it,
// so a build is classified once, whole, instead of once per finished building.
//
// A malicious player can still delay the scan of its own art by keeping a builder busy next to it.
// The wait is capped, so the art is sent at most MAX_WAIT_TICKS after its first building.
final class GroupCollector<T extends MindustryImage> {

    private static final int MAX_GROUP_STEPS = 50;
    // Bounds the cost of a tick when a lot of separate buildings are waiting
    private static final int MAX_REGION_CHECKS = 16;
    // Buildings of the same image can be this far apart, large blocks included
    private static final int REGION_MARGIN = 8;
    // Plans this close to a region are considered part of its build
    private static final int BUSY_MARGIN = 16;
    // Time without new buildings in a region before it can be grouped, covers the gaps between plans
    private static final long QUIET_TICKS = 90;
    // Regions are grouped after that long even if their builders are still at it
    private static final long MAX_WAIT_TICKS = 10 * 60 * 60L;

    private final VirtualBuildingIndex<T> index;
    private final GroupClassifier classifier;
    private final BuilderActivity activity;
    private final Predicate<Block> blocks;
    private final Predicate<VirtualBuilding<T>> isEligibleAnchor;
    private final Predicate<VirtualBuilding.Group<T>> isEligibleGroup;
    private final int maxGroupRange;
    private final List<Region> regions = new ArrayList<>();
    private int cursor = 0;
    private VirtualBuildingIndex<T>.@Nullable IncrementalGrouper grouper = null;

    GroupCollector(
            final VirtualBuildingIndex<T> index,
            final GroupClassifier classifier,
            final BuilderActivity activity,
            final Predicate<Block> blocks,
            final Predicate<VirtualBuilding<T>> isEligibleAnchor,
            final Predicate<VirtualBuilding.Group<T>> isEligibleGroup,
            final int maxGroupRange) {
        this.index = index;
        this.classifier = classifier;
        this.activity = activity;
        this.blocks = blocks;
        this.isEligibleAnchor = isEligibleAnchor;
        this.isEligibleGroup = isEligibleGroup;
        this.maxGroupRange = maxGroupRange;
    }

    public void enqueue(final int packed) {
        if (this.grouper != null && this.grouper.isVisited(packed)) {
            return;
        }

        final int x = GeometryUtils.x(packed);
        final int y = GeometryUtils.y(packed);
        final long now = now();
        Region region = null;
        boolean merged = false;
        for (final var candidate : this.regions) {
            if (!candidate.isNear(x, y, REGION_MARGIN)) {
                continue;
            }
            if (region == null) {
                region = candidate;
                continue;
            }
            // Always move the smaller one, so merging stays cheap
            final var larger = region.anchors.size() >= candidate.anchors.size() ? region : candidate;
            final var smaller = larger == region ? candidate : region;
            larger.merge(smaller);
            smaller.anchors.clear();
            region = larger;
            merged = true;
        }
        if (merged) {
            this.regions.removeIf(candidate -> candidate.anchors.isEmpty());
        }
        if (region == null) {
            region = new Region(now);
            this.regions.add(region);
        }
        region.add(packed, x, y, now);
    }

    public void dequeue(final int packed) {
        this.regions.removeIf(region -> region.anchors.remove(packed) && region.anchors.isEmpty());
    }

    public void clear() {
        this.regions.clear();
        this.grouper = null;
    }

    public void tick() {
        if (this.grouper != null) {
            this.continueGrouping(this.grouper);
            return;
        }
        if (this.regions.isEmpty()) {
            return;
        }

        final long now = now();
        this.activity.refresh(now);
        final int size = this.regions.size();
        final int checks = Math.min(size, MAX_REGION_CHECKS);
        for (int i = 0; i < checks; i++) {
            final int index = Math.floorMod(this.cursor + i, size);
            final var region = this.regions.get(index);
            if (!this.isSettled(region, now)) {
                continue;
            }
            this.cursor = index;
            final var anchor = this.selectAnchor(region);
            if (anchor == null) {
                this.regions.remove(index);
                return;
            }
            this.grouper = this.index.selectGroupWithinRangeIncremental(
                    anchor.x(), anchor.y(), this.maxGroupRange, MAX_GROUP_STEPS);
            this.continueGrouping(this.grouper);
            return;
        }
        // The regions are checked in turns, so that none is starved by the others
        this.cursor = Math.floorMod(this.cursor + checks, size);
    }

    private boolean isSettled(final Region region, final long now) {
        if (now < region.createdAt) {
            // The clock was reset
            region.createdAt = now;
            region.touchedAt = now;
        }
        if (now - region.createdAt >= MAX_WAIT_TICKS) {
            return true;
        }
        if (now - region.touchedAt < QUIET_TICKS) {
            return false;
        }
        return !this.activity.isBuildingWithin(
                region.minX - BUSY_MARGIN,
                region.minY - BUSY_MARGIN,
                region.maxX + BUSY_MARGIN,
                region.maxY + BUSY_MARGIN,
                this.blocks,
                now);
    }

    private @Nullable VirtualBuilding<T> selectAnchor(final Region region) {
        final var iterator = region.anchors.iterator();
        while (iterator.hasNext()) {
            final int point = iterator.next();
            final var anchor = this.index.select(GeometryUtils.x(point), GeometryUtils.y(point));
            if (anchor != null && this.isEligibleAnchor.test(anchor)) {
                return anchor;
            }
            iterator.remove();
        }
        return null;
    }

    private void continueGrouping(final VirtualBuildingIndex<T>.IncrementalGrouper grouper) {
        if (!grouper.isCompleted()) {
            grouper.progress();
            if (!grouper.isCompleted()) {
                return;
            }
            // Visited buildings cannot be enqueued while grouping, so the regions only need to be cleaned once
            this.regions.removeIf(region -> {
                grouper.removeVisited(region.anchors);
                return region.anchors.isEmpty();
            });
        }
        final var group = grouper.create();
        if (group == null || !this.isEligibleGroup.test(group) || this.classifier.tryAccept(group)) {
            this.grouper = null;
        }
    }

    private static long now() {
        return (long) Vars.state.tick;
    }

    private static final class Region {

        private final SequencedSet<Integer> anchors = new LinkedHashSet<>();
        private long createdAt;
        private long touchedAt;
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;

        private Region(final long now) {
            this.createdAt = now;
            this.touchedAt = now;
        }

        private void add(final int packed, final int x, final int y, final long now) {
            this.anchors.add(packed);
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
            this.touchedAt = now;
        }

        private void merge(final Region other) {
            this.anchors.addAll(other.anchors);
            this.createdAt = Math.min(this.createdAt, other.createdAt);
            this.touchedAt = Math.max(this.touchedAt, other.touchedAt);
            this.minX = Math.min(this.minX, other.minX);
            this.minY = Math.min(this.minY, other.minY);
            this.maxX = Math.max(this.maxX, other.maxX);
            this.maxY = Math.max(this.maxY, other.maxY);
        }

        private boolean isNear(final int x, final int y, final int margin) {
            return this.minX - margin <= x
                    && x <= this.maxX + margin
                    && this.minY - margin <= y
                    && y <= this.maxY + margin;
        }
    }
}
