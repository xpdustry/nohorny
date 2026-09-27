// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.function.Predicate;
import mindustry.world.Block;
import org.jspecify.annotations.Nullable;

// Groups the queued buildings a few steps per tick, then hands the groups over to the classifier
final class GroupCollector<T extends MindustryImage> {

    private final VirtualBuildingIndex<T> index;
    private final GroupClassifier classifier;
    private final Predicate<Block> blocks;
    private final Predicate<VirtualBuilding<T>> isEligibleAnchor;
    private final Predicate<VirtualBuilding.Group<T>> isEligibleGroup;
    private final int maxGroupRange;
    private final int maxGroupSteps;
    private final SequencedSet<Integer> queue = new LinkedHashSet<>();
    private final WaitForTheBuildToFinish waiter = new WaitForTheBuildToFinish();
    private VirtualBuildingIndex<T>.@Nullable IncrementalGrouper grouper = null;

    GroupCollector(
            final VirtualBuildingIndex<T> index,
            final GroupClassifier classifier,
            final Predicate<Block> blocks,
            final Predicate<VirtualBuilding<T>> isEligibleAnchor,
            final Predicate<VirtualBuilding.Group<T>> isEligibleGroup,
            final int maxGroupRange,
            final int maxGroupSteps) {
        this.index = index;
        this.classifier = classifier;
        this.blocks = blocks;
        this.isEligibleAnchor = isEligibleAnchor;
        this.isEligibleGroup = isEligibleGroup;
        this.maxGroupRange = maxGroupRange;
        this.maxGroupSteps = maxGroupSteps;
    }

    public void enqueue(final int packed) {
        if (this.grouper != null && this.grouper.isVisited(packed)) {
            return;
        }
        this.queue.addLast(packed);
    }

    public void dequeue(final int packed) {
        this.queue.remove(packed);
    }

    public void clear() {
        this.queue.clear();
        this.grouper = null;
    }

    public void tick() {
        if (this.grouper != null) {
            this.continueGrouping(this.grouper);
            return;
        }

        while (!this.queue.isEmpty()) {
            final int point = this.queue.removeFirst();
            final var x = GeometryUtils.x(point);
            final var y = GeometryUtils.y(point);
            final var anchor = this.index.select(x, y);
            if (anchor == null || !this.isEligibleAnchor.test(anchor)) {
                continue;
            }
            this.waiter.estimateWaitTimeFor(this.blocks);
            this.grouper = this.index.selectGroupWithinRangeIncremental(x, y, this.maxGroupRange, this.maxGroupSteps);
            this.continueGrouping(this.grouper);
            break;
        }
    }

    private void continueGrouping(final VirtualBuildingIndex<T>.IncrementalGrouper grouper) {
        if (this.waiter.isNotDone()) {
            this.waiter.countdown();
            return;
        }
        if (!grouper.isCompleted()) {
            grouper.progress();
            if (!grouper.isCompleted()) {
                return;
            }
            // Visited buildings cannot be enqueued while grouping, so the queue only needs to be cleaned once
            this.queue.removeIf(grouper::isVisited);
        }
        final var group = grouper.create();
        if (group == null || !this.isEligibleGroup.test(group) || this.classifier.tryAccept(group)) {
            this.grouper = null;
        }
    }
}
