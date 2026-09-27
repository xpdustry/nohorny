// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.world.Block;
import org.jspecify.annotations.Nullable;

// Tracks buildings whose content is fully described by their own state, grouping adjacent ones into a single image
abstract class BuildingImageTracker<B extends Building, T extends MindustryImage> implements LifecycleListener {

    final VirtualBuildingIndex<T> index = new VirtualBuildingIndex<>();
    final Class<B> buildingType;
    private final Class<? extends Block> blockType;
    private final NoHornyClient client;
    private final int maxGroupRange;
    private final int maxGroupSteps;
    private final int minGroupSize;
    private final SequencedSet<Integer> queue = new LinkedHashSet<>();
    private final WaitForTheBuildToFinish waiter = new WaitForTheBuildToFinish();
    private VirtualBuildingIndex<T>.@Nullable IncrementalGrouper grouper = null;

    protected BuildingImageTracker(
            final NoHornyClient client,
            final Class<B> buildingType,
            final Class<? extends Block> blockType,
            final int maxGroupRange,
            final int maxGroupSteps,
            final int minGroupSize) {
        this.client = client;
        this.buildingType = buildingType;
        this.blockType = blockType;
        this.maxGroupRange = maxGroupRange;
        this.maxGroupSteps = maxGroupSteps;
        this.minGroupSize = minGroupSize;
    }

    protected abstract T data(final B building, final @Nullable MindustryAuthor author);

    protected boolean isEligible(final VirtualBuilding<T> anchor) {
        return true;
    }

    protected boolean isEligible(final VirtualBuilding.Group<T> group) {
        return group.w() >= this.minGroupSize && group.h() >= this.minGroupSize;
    }

    @Override
    public void onInit() {
        MindustryUtils.onEvent(this.buildingType, new BuildingLifecycleEventListener<>() {
            @Override
            public void onCreate(final B building, final @Nullable MindustryAuthor author, final boolean queue) {
                BuildingImageTracker.this.upsert(building, author, queue);
            }

            @Override
            public void onRemoveAll() {
                BuildingImageTracker.this.index.removeAll();
                BuildingImageTracker.this.queue.clear();
                BuildingImageTracker.this.grouper = null;
            }

            @Override
            public void onRemove(final int x, final int y, final int size) {
                for (final var removed : BuildingImageTracker.this.index.removeAllWithinSquare(x, y, size)) {
                    BuildingImageTracker.this.queue.remove(removed.packed());
                }
            }
        });

        MindustryUtils.onEvent(EventType.Trigger.update, _ -> this.collect());
    }

    protected final void upsert(final B building, final @Nullable MindustryAuthor author, final boolean queue) {
        final var x = MindustryUtils.anchorTileX(building);
        final var y = MindustryUtils.anchorTileY(building);
        final var added = this.index.upsert(x, y, building.block.size, this.data(building, author));
        if (queue) {
            this.enqueue(added.packed());
        }
    }

    private void collect() {
        if (!Vars.state.isGame()) {
            return;
        }

        if (this.grouper != null) {
            this.continueGrouperProcessing();
            return;
        }

        while (!this.queue.isEmpty()) {
            final int point = this.queue.removeFirst();
            final var x = GeometryUtils.x(point);
            final var y = GeometryUtils.y(point);
            final var anchor = this.index.select(x, y);
            if (anchor == null || !this.isEligible(anchor)) {
                continue;
            }
            this.waiter.estimateWaitTimeFor(this.blockType::isInstance);
            this.grouper = this.index.selectGroupWithinRangeIncremental(x, y, this.maxGroupRange, this.maxGroupSteps);
            this.continueGrouperProcessing();
            break;
        }
    }

    private void continueGrouperProcessing() {
        Objects.requireNonNull(this.grouper);
        if (this.waiter.isNotDone()) {
            this.waiter.countdown();
            return;
        }
        this.grouper.progress();
        this.queue.removeIf(this.grouper::isVisited);
        if (this.grouper.isCompleted()) {
            final var group = this.grouper.create();
            if (group == null || !this.isEligible(group)) {
                this.grouper = null;
                return;
            }
            if (this.client.tryAccept(group)) {
                this.grouper = null;
            }
        }
    }

    private void enqueue(final int packed) {
        if (this.grouper != null && this.grouper.isVisited(packed)) {
            return;
        }
        this.queue.addLast(packed);
    }
}
