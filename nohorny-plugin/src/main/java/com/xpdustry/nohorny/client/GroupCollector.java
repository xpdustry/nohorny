// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.SequencedSet;
import java.util.function.Predicate;
import mindustry.Vars;
import mindustry.world.Block;
import org.jspecify.annotations.Nullable;

// Groups the queued buildings a few steps per tick and sends each group to the classifier.
//
// Queued buildings are batched per author, a batch is only grouped once its author is done building,
// so a build is classified once, whole, instead of once per finished building.
//
// A malicious player can still delay the scan of its own art by keeping a builder busy.
// The wait is capped, so the art is sent at most MAX_WAIT_TICKS after its first building.
final class GroupCollector<T extends MindustryImage> {

    private static final int MAX_GROUP_STEPS = 50;
    // Time without new buildings from an author before its batch can be grouped, covers the gaps between plans
    private static final long QUIET_TICKS = 90;
    // Batches are grouped after that long even if their author is still at it
    private static final long MAX_WAIT_TICKS = 15 * 60L;
    // For the buildings without a known author, like the ones changing team
    private static final String UNKNOWN_AUTHOR = "";

    private final VirtualBuildingIndex<T> index;
    private final GroupClassifier classifier;
    private final BuilderActivity activity;
    private final Predicate<Block> blocks;
    private final Predicate<VirtualBuilding<T>> isEligibleAnchor;
    private final Predicate<VirtualBuilding.Group<T>> isEligibleGroup;
    private final int maxGroupRange;
    private final Map<String, Batch> batches = new LinkedHashMap<>();
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

    public void enqueue(final int packed, final @Nullable MindustryAuthor author) {
        if (this.grouper != null && this.grouper.isVisited(packed)) {
            return;
        }
        final var key = author == null ? UNKNOWN_AUTHOR : author.uuid();
        final long now = now();
        final var batch = this.batches.computeIfAbsent(key, _ -> new Batch(now));
        batch.anchors.add(packed);
        batch.touchedAt = now;
    }

    public void dequeue(final int packed) {
        this.batches.values().removeIf(batch -> batch.anchors.remove(packed) && batch.anchors.isEmpty());
    }

    public void clear() {
        this.batches.clear();
        this.grouper = null;
    }

    public void tick() {
        if (this.grouper != null) {
            this.continueGrouping(this.grouper);
            return;
        }
        if (this.batches.isEmpty()) {
            return;
        }

        final long now = now();
        this.activity.refresh(now);
        final var iterator = this.batches.entrySet().iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            if (!this.isSettled(entry.getKey(), entry.getValue(), now)) {
                continue;
            }
            final var anchor = this.selectAnchor(entry.getValue());
            if (anchor == null) {
                iterator.remove();
                continue;
            }
            // What is built from now on is a new wait
            entry.getValue().createdAt = now;
            this.grouper = this.index.selectGroupWithinRangeIncremental(
                    anchor.x(), anchor.y(), this.maxGroupRange, MAX_GROUP_STEPS);
            this.continueGrouping(this.grouper);
            return;
        }
    }

    private boolean isSettled(final String author, final Batch batch, final long now) {
        if (now < batch.createdAt) {
            // The clock was reset
            batch.createdAt = now;
            batch.touchedAt = now;
        }
        if (now - batch.createdAt >= MAX_WAIT_TICKS) {
            return true;
        }
        return now - batch.touchedAt >= QUIET_TICKS && !this.activity.isBuilding(author, this.blocks, now);
    }

    private @Nullable VirtualBuilding<T> selectAnchor(final Batch batch) {
        final var iterator = batch.anchors.iterator();
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
            // Visited buildings cannot be enqueued while grouping, so the batches only need to be cleaned once
            this.batches.values().removeIf(batch -> {
                grouper.removeVisited(batch.anchors);
                return batch.anchors.isEmpty();
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

    private static final class Batch {

        private final SequencedSet<Integer> anchors = new LinkedHashSet<>();
        private long createdAt;
        private long touchedAt;

        private Batch(final long now) {
            this.createdAt = now;
            this.touchedAt = now;
        }
    }
}
