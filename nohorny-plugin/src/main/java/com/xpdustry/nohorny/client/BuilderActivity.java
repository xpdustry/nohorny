// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.GeometryUtils;
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
// The server only sees the first plans of a unit queue, which is enough to tell that a builder is still at it.
final class BuilderActivity {

    private static final int REFRESH_INTERVAL = 10;
    // A builder that makes no progress for that long is considered gone (out of range, out of resources, afk)
    private static final long STALL_TICKS = 10 * 60L;

    private final Map<String, Builder> builders = new HashMap<>();
    private long refreshedAt = -1;

    public void refresh(final long now) {
        if (this.refreshedAt >= 0 && now >= this.refreshedAt && now - this.refreshedAt < REFRESH_INTERVAL) {
            return;
        }
        this.refreshedAt = now;
        this.builders.values().forEach(builder -> builder.seen = false);
        for (final var player : Groups.player) {
            this.builders
                    .computeIfAbsent(player.uuid(), _ -> new Builder(player))
                    .update(player, now);
        }
        this.builders.values().removeIf(builder -> !builder.seen);
    }

    // Whether the player still makes progress on a plan for a matching block
    public boolean isBuilding(final String uuid, final Predicate<Block> blocks, final long now) {
        final var builder = this.builders.get(uuid);
        return builder != null && builder.isActive(now) && builder.hasPlan(blocks);
    }

    private static final class Builder {

        private boolean seen = false;
        private Player player;
        private long lastProgress = 0;
        private int lastPlan = -1;
        private double lastPlanProgress = Double.NaN;

        private Builder(final Player player) {
            this.player = player;
        }

        void update(final Player player, final long now) {
            this.seen = true;
            this.player = player;
            final var unit = player.unit();
            if (unit == null || unit.dead || unit.plans.isEmpty()) {
                this.lastProgress = now;
                this.lastPlan = -1;
                return;
            }
            this.trackProgress(unit.buildPlan(), now);
        }

        // Plans do not change while a block is under construction, so the progress of the block tells it moves
        private void trackProgress(final BuildPlan plan, final long now) {
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

        boolean isActive(final long now) {
            final var unit = this.player.unit();
            return unit != null && !unit.dead && !unit.plans.isEmpty() && now - this.lastProgress <= STALL_TICKS;
        }

        boolean hasPlan(final Predicate<Block> predicate) {
            final var plans = this.player.unit().plans;
            for (int i = 0; i < plans.size; i++) {
                final var plan = plans.get(i);
                if (!plan.breaking && predicate.test(plan.block)) {
                    return true;
                }
            }
            return false;
        }
    }
}
