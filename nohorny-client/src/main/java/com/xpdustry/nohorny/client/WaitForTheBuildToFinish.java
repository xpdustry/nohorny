// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.util.function.Predicate;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.world.Block;

final class WaitForTheBuildToFinish {

    private int counter = 0;

    public void estimateWaitTimeFor(final Predicate<Block> predicate) {
        for (final var player : Groups.player) {
            int ticks = 5;
            for (final var plan : player.getPreviewPlans()) {
                final var unit = player.unit();
                final var block = plan.block;
                if (!predicate.test(block)) {
                    continue;
                }
                if (unit == null
                        || Vars.state.rules.infiniteResources
                        || unit.team().rules().infiniteResources) {
                    ticks += 1;
                } else {
                    final var buildTime = block.buildTime * Vars.state.rules.buildCostMultiplier;
                    final var buildSpeed = unit.type().buildSpeed
                            * unit.buildSpeedMultiplier()
                            * Vars.state.rules.buildSpeed(unit.team());
                    ticks += (int) Math.ceil(buildTime / buildSpeed);
                }
            }
            this.counter = Math.max(this.counter, ticks);
        }
    }

    public void countdown() {
        this.counter = Math.max(this.counter - 1, 0);
    }

    public boolean isNotDone() {
        return this.counter > 0;
    }
}
