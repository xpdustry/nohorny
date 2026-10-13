// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.struct.Seq;
import com.xpdustry.nohorny.common.GeometryUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock;

// Tracks what the players are building right now, to know when a build is over.
// The server only sees the first plans of a unit queue, the preview plans extend the view when the client sends them.
final class BuilderActivity {

    private static final int REFRESH_INTERVAL = 10;
    // A builder that makes no progress for that long is considered gone (out of range, out of resources, afk)
    private static final long STALL_TICKS = 20 * 60L;

    private final Map<String, Builder> builders = new HashMap<>();
    private long refreshedAt = -1;

    public void refresh(final long now) {
        if (this.refreshedAt >= 0 && now >= this.refreshedAt && now - this.refreshedAt < REFRESH_INTERVAL) {
            return;
        }
        this.refreshedAt = now;
        this.builders.values().forEach(builder -> builder.seen = false);
        for (final var player : Groups.player) {
            this.builders.computeIfAbsent(player.uuid(), _ -> new Builder()).update(player, now);
        }
        this.builders.values().removeIf(builder -> !builder.seen);
    }

    // Whether a player that still makes progress has a plan for a matching block within the (inclusive) tile area
    public boolean isBuildingWithin(
            final int x1, final int y1, final int x2, final int y2, final Predicate<Block> blocks, final long now) {
        for (final var builder : this.builders.values()) {
            if (builder.isActive(now) && builder.hasPlanWithin(x1, y1, x2, y2, blocks)) {
                return true;
            }
        }
        return false;
    }

    private static final class Builder {

        private boolean seen = false;
        private boolean active = false;
        private long lastProgress = 0;
        private double lastPlanProgress = Double.NaN;
        private int lastPlan = -1;
        private int[] positions = new int[0];
        private Block[] blocks = new Block[0];
        private int size = 0;

        void update(final Player player, final long now) {
            this.seen = true;
            final var unit = player.unit();
            if (unit == null || unit.dead || unit.plans.isEmpty()) {
                this.active = false;
                this.size = 0;
                this.lastProgress = now;
                this.lastPlan = -1;
                return;
            }
            this.active = true;
            this.size = 0;
            for (final var plan : unit.plans) {
                this.add(plan);
            }
            final Seq<BuildPlan> preview = player.getPreviewPlans();
            for (final var plan : preview) {
                this.add(plan);
            }
            this.trackProgress(unit.buildPlan(), now);
        }

        // Plans do not change while a block is under construction, so the progress of the block tells it moves
        private void trackProgress(final BuildPlan plan, final long now) {
            if (plan == null) {
                return;
            }
            final var tile = Vars.world.tile(plan.x, plan.y);
            final double progress = tile != null && tile.build instanceof ConstructBlock.ConstructBuild construct
                    ? construct.progress
                    : Double.NaN;
            final int position = GeometryUtils.pack(plan.x, plan.y);
            if (position != this.lastPlan || Double.compare(progress, this.lastPlanProgress) != 0) {
                this.lastProgress = now;
            }
            this.lastPlan = position;
            this.lastPlanProgress = progress;
        }

        private void add(final BuildPlan plan) {
            if (plan.breaking || plan.block == null) {
                return;
            }
            if (this.size == this.positions.length) {
                final int capacity = Math.max(32, this.size * 2);
                this.positions = Arrays.copyOf(this.positions, capacity);
                this.blocks = Arrays.copyOf(this.blocks, capacity);
            }
            this.positions[this.size] = GeometryUtils.pack(plan.x, plan.y);
            this.blocks[this.size] = plan.block;
            this.size++;
        }

        boolean isActive(final long now) {
            return this.active && now - this.lastProgress <= STALL_TICKS;
        }

        boolean hasPlanWithin(
                final int x1, final int y1, final int x2, final int y2, final Predicate<Block> predicate) {
            for (int i = 0; i < this.size; i++) {
                final int x = GeometryUtils.x(this.positions[i]);
                final int y = GeometryUtils.y(this.positions[i]);
                if (x1 <= x && x <= x2 && y1 <= y && y <= y2 && predicate.test(this.blocks[i])) {
                    return true;
                }
            }
            return false;
        }
    }
}
