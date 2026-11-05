// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.IntMap;
import arc.struct.IntSet;
import com.xpdustry.nohorny.common.ArtKind;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Function;
import java.util.function.Predicate;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.world.Block;
import org.jspecify.annotations.Nullable;

// Queued buildings are batched per author, a batch is only grouped once its author is done building,
// so a build is classified once, whole, instead of once per finished building.
// A building waits in a single batch, the one of the first author that changed it,
// the changes of the other authors keep that batch waiting.
//
// A player that keeps building, or just keeps a plan it never gets to finish, delays the scan of its own art.
// The wait is capped, so the art is sent at most MAX_WAIT_TICKS after its first building.
final class GroupCollector<T extends MindustryImage> {

    static final int MAX_GROUP_STEPS = 50;
    // Unqueuing a grouped building is two map removals, much cheaper than a grouping step
    static final int MAX_CLEANUP_STEPS = 1000;
    // Checking an anchor can scan a whole canvas
    static final int MAX_ANCHOR_CHECKS = 100;
    // Covers the gaps between the plans of an author
    static final long QUIET_TICKS = 90;
    static final long MAX_WAIT_TICKS = 15 * 60L;
    private static final MUUID UNKNOWN_AUTHOR = new MUUID("", "");

    private final VirtualBuildingIndex<T> index;
    private final GroupClassifier classifier;
    private final ArtKind kind;
    private final Predicate<Block> blocks;
    private final Predicate<VirtualBuilding<T>> isEligibleAnchor;
    private final Predicate<VirtualBuilding.Group<T>> isEligibleGroup;
    private final int maxGroupRange;
    private final Function<MUUID, @Nullable Player> players;
    private final Map<MUUID, Batch> batches = new LinkedHashMap<>();
    private final IntMap<Batch> queued = new IntMap<>();
    private @Nullable Grouping grouping = null;

    GroupCollector(
            final VirtualBuildingIndex<T> index,
            final GroupClassifier classifier,
            final ArtKind kind,
            final Predicate<Block> blocks,
            final Predicate<VirtualBuilding<T>> isEligibleAnchor,
            final Predicate<VirtualBuilding.Group<T>> isEligibleGroup,
            final int maxGroupRange,
            final Function<MUUID, @Nullable Player> players) {
        this.index = index;
        this.classifier = classifier;
        this.kind = kind;
        this.blocks = blocks;
        this.isEligibleAnchor = isEligibleAnchor;
        this.isEligibleGroup = isEligibleGroup;
        this.maxGroupRange = maxGroupRange;
        this.players = players;
    }

    public void enqueue(final int packed, final @Nullable MindustryAuthor author) {
        if (this.grouping != null && this.grouping.grouper.isVisited(packed)) {
            return;
        }
        final long now = now();
        var batch = this.queued.get(packed);
        if (batch == null) {
            final var key = author == null ? UNKNOWN_AUTHOR : MUUID.from(author);
            batch = this.batches.computeIfAbsent(key, _ -> new Batch(key, now));
            batch.queuedAtTick.put(packed, now);
            this.queued.put(packed, batch);
        }
        batch.touchedAt = now;
    }

    public void dequeue(final int packed) {
        final var batch = this.queued.remove(packed);
        if (batch != null) {
            batch.queuedAtTick.remove(packed);
            if (batch.queuedAtTick.isEmpty()) {
                this.batches.remove(batch.author);
            }
        }
    }

    public void clear() {
        this.batches.clear();
        this.queued.clear();
        this.grouping = null;
    }

    int size() {
        return this.queued.size;
    }

    public void tick() {
        if (this.grouping != null) {
            this.continueGrouping(this.grouping);
            return;
        }
        if (this.batches.isEmpty()) {
            return;
        }

        final long now = now();
        int checks = MAX_ANCHOR_CHECKS;
        final var iterator = this.batches.values().iterator();
        while (iterator.hasNext() && checks > 0) {
            final var batch = iterator.next();
            if (!this.isSettled(batch, now)) {
                continue;
            }
            final var anchors = batch.queuedAtTick.keySet().iterator();
            while (anchors.hasNext() && checks > 0) {
                checks--;
                final int point = anchors.next();
                final var anchor = this.index.select(GeometryUtils.x(point), GeometryUtils.y(point));
                if (anchor != null && this.isEligibleAnchor.test(anchor)) {
                    this.grouping = new Grouping(this.index.selectGroupWithinRangeIncremental(
                            anchor.x(), anchor.y(), this.maxGroupRange, MAX_GROUP_STEPS));
                    this.continueGrouping(this.grouping);
                    return;
                }
                anchors.remove();
                this.queued.remove(point);
            }
            if (batch.queuedAtTick.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private boolean isSettled(final Batch batch, final long now) {
        final long oldest = batch.queuedAtTick.firstEntry().getValue();
        if (now < oldest) {
            // The clock was reset
            batch.queuedAtTick.replaceAll((_, _) -> now);
            batch.touchedAt = now;
        }
        if (now - oldest >= MAX_WAIT_TICKS) {
            return true;
        }
        return now - batch.touchedAt >= QUIET_TICKS && !this.isBuilding(batch.author);
    }

    // The server only sees the first plans of a unit queue, which is enough to tell that the player is still at it
    private boolean isBuilding(final MUUID author) {
        final var player = this.players.apply(author);
        final var unit = player == null ? null : player.unit();
        if (unit == null || unit.dead) {
            return false;
        }
        for (int i = 0; i < unit.plans.size; i++) {
            final var plan = unit.plans.get(i);
            if (!plan.breaking && this.blocks.test(plan.block)) {
                return true;
            }
        }
        return false;
    }

    private void continueGrouping(final Grouping grouping) {
        final var grouper = grouping.grouper;
        if (!grouper.isCompleted()) {
            grouper.progress();
            if (!grouper.isCompleted()) {
                return;
            }
        }
        // Visited buildings cannot be enqueued while grouping, so the batches only need to be cleaned once
        if (grouping.visitedToUnqueue == null) {
            grouping.visitedToUnqueue = grouper.visited();
        }
        for (int i = 0; i < MAX_CLEANUP_STEPS && grouping.visitedToUnqueue.hasNext; i++) {
            this.dequeue(grouping.visitedToUnqueue.next());
        }
        if (grouping.visitedToUnqueue.hasNext) {
            return;
        }
        final var group = grouper.create();
        if (group == null || !this.isEligibleGroup.test(group) || this.classifier.tryAccept(group, this.kind)) {
            this.grouping = null;
        }
    }

    private static long now() {
        return (long) Vars.state.tick;
    }

    private final class Grouping {

        private final VirtualBuildingIndex<T>.IncrementalGrouper grouper;
        private IntSet.@Nullable IntSetIterator visitedToUnqueue = null;

        private Grouping(final VirtualBuildingIndex<T>.IncrementalGrouper grouper) {
            this.grouper = grouper;
        }
    }

    private static final class Batch {

        private final MUUID author;
        private final SequencedMap<Integer, Long> queuedAtTick = new LinkedHashMap<>();
        private long touchedAt;

        private Batch(final MUUID author, final long now) {
            this.author = author;
            this.touchedAt = now;
        }
    }
}
