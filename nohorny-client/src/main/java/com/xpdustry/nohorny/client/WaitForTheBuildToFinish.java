// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.math.Mathf;
import arc.util.Time;
import java.util.function.Predicate;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.world.Block;

// TODO
//  Fix possible exploit where a malicious player can fill up its build queue
//  and cancel it to the delay initial scan.
//  Leaving time for other players to see the unsafe buildings, then delete
final class WaitForTheBuildToFinish {

    private long counter = 0;

    public void estimateWaitTimeFor(final Predicate<Block> predicate) {
        for (final var player : Groups.player) {
            float ticks = 5f;
            final var unit = player.unit();
            if (unit == null) {
                continue;
            }
            for (final var plan : player.getPreviewPlans()) {
                final var block = plan.block;
                if (!predicate.test(block)) {
                    continue;
                }
                if (Vars.state.rules.infiniteResources) {
                    ticks += 1F;
                } else {
                    final float buildTime = block.buildTime * Vars.state.rules.buildCostMultiplier;
                    final float buildSpeed = unit.type().buildSpeed
                            * unit.buildSpeedMultiplier()
                            * Vars.state.rules.buildSpeed(unit.team());
                    ticks += Mathf.ceil(buildTime / Math.max(buildSpeed, 0.1F));
                }
            }
            this.counter = (long) Math.min(15 * Time.toSeconds, ticks);
        }
    }

    public void countdown() {
        this.counter = Math.max(this.counter - 1, 0);
    }

    public boolean isNotDone() {
        return this.counter > 0;
    }
}
