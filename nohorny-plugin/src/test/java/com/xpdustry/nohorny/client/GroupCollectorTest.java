// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.ArtKind;
import com.xpdustry.nohorny.common.GeometryUtils;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryPixel;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import mindustry.Vars;
import mindustry.core.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.xpdustry.nohorny.client.GroupCollector.MAX_ANCHOR_CHECKS;
import static com.xpdustry.nohorny.client.GroupCollector.MAX_CLEANUP_STEPS;
import static com.xpdustry.nohorny.client.GroupCollector.MAX_GROUP_STEPS;
import static com.xpdustry.nohorny.client.GroupCollector.MAX_WAIT_TICKS;
import static com.xpdustry.nohorny.client.GroupCollector.QUIET_TICKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GroupCollectorTest {

    // Batches are settled once they waited that long, whatever their author does
    private static final long SETTLED = MAX_WAIT_TICKS;

    private static final MindustryAuthor ALICE = new MindustryAuthor("alice", "alice-usid", "0.0.0.1");
    private static final MindustryAuthor BOB = new MindustryAuthor("bob", "bob-usid", "0.0.0.2");

    private final VirtualBuildingIndex<MindustryPixel> index = new VirtualBuildingIndex<>();
    private final List<VirtualBuilding.Group<MindustryPixel>> groups = new ArrayList<>();

    @BeforeEach
    void setup() {
        Vars.state = new GameState();
        Vars.state.tick = 0;
    }

    @Test
    void complete_a_grouping_in_one_tick_per_max_group_steps() {
        // Not a multiple of the steps nor of the cleanup budget
        fill(37, 41);
        final var collector = this.collector(_ -> true, 150);
        collector.enqueue(GeometryUtils.pack(18, 20), ALICE);
        Vars.state.tick = SETTLED;

        final int n = 37 * 41;
        // The last grouping tick also starts the cleanup
        assertEquals(ceil(n, MAX_GROUP_STEPS) + ceil(n, MAX_CLEANUP_STEPS) - 1, this.ticksUntilGroup(collector));
        assertEquals(n, this.groups.getFirst().elements().size());
    }

    @Test
    void group_at_most_the_bounding_box() {
        // The range clips the field, a range of 150 around the anchor covers 300x300 pixels
        fill(400, 400);
        final var collector = this.collector(_ -> true, 150);
        collector.enqueue(GeometryUtils.pack(200, 200), ALICE);
        Vars.state.tick = SETTLED;

        final int n = 300 * 300;
        assertEquals(ceil(n, MAX_GROUP_STEPS) + ceil(n, MAX_CLEANUP_STEPS) - 1, this.ticksUntilGroup(collector));
        assertEquals(n, this.groups.getFirst().elements().size());
    }

    @Test
    void limit_the_cleanup_per_tick() {
        fill(100, 100);
        final var collector = this.collector(_ -> true, 150);
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                collector.enqueue(GeometryUtils.pack(x, y), ALICE);
            }
        }
        Vars.state.tick = SETTLED;

        int previous = collector.size();
        while (this.groups.isEmpty()) {
            collector.tick();
            assertTrue(previous - collector.size() <= MAX_CLEANUP_STEPS, "unqueued too many buildings in one tick");
            previous = collector.size();
        }
        assertEquals(0, collector.size());
        assertEquals(100 * 100, this.groups.getFirst().elements().size());
    }

    @Test
    void limit_the_anchor_checks_per_tick() {
        // Ten ticks worth of ineligible anchors
        fill(MAX_ANCHOR_CHECKS, 10);
        final var collector = this.collector(_ -> false, 150);
        for (int x = 0; x < MAX_ANCHOR_CHECKS; x++) {
            for (int y = 0; y < 10; y++) {
                collector.enqueue(GeometryUtils.pack(x, y), ALICE);
            }
        }
        Vars.state.tick = SETTLED;

        for (int i = 1; i <= 10; i++) {
            collector.tick();
            assertEquals((10 - i) * MAX_ANCHOR_CHECKS, collector.size());
        }
        assertTrue(this.groups.isEmpty());
    }

    @Test
    void wait_for_every_author_of_a_building() {
        fill(1, 1);
        final var collector = this.collector(_ -> true, 150);
        collector.enqueue(GeometryUtils.pack(0, 0), ALICE);
        final long bobTick = QUIET_TICKS - 10;
        Vars.state.tick = bobTick;
        collector.enqueue(GeometryUtils.pack(0, 0), BOB);
        assertEquals(1, collector.size());

        // Alice is quiet, but Bob changed the building since
        Vars.state.tick = QUIET_TICKS;
        collector.tick();
        assertTrue(this.groups.isEmpty());

        Vars.state.tick = bobTick + QUIET_TICKS;
        collector.tick();
        assertEquals(1, this.groups.size());
    }

    @Test
    void dequeue_a_removed_building() {
        fill(2, 1);
        final var collector = this.collector(_ -> true, 150);
        collector.enqueue(GeometryUtils.pack(0, 0), ALICE);
        collector.enqueue(GeometryUtils.pack(1, 0), BOB);

        collector.dequeue(GeometryUtils.pack(0, 0));
        collector.dequeue(GeometryUtils.pack(1, 0));

        assertEquals(0, collector.size());
        Vars.state.tick = SETTLED;
        collector.tick();
        assertTrue(this.groups.isEmpty());
    }

    private int ticksUntilGroup(final GroupCollector<MindustryPixel> collector) {
        int ticks = 0;
        while (this.groups.isEmpty()) {
            collector.tick();
            ticks++;
        }
        return ticks;
    }

    private GroupCollector<MindustryPixel> collector(
            final Predicate<VirtualBuilding<MindustryPixel>> isEligibleAnchor, final int range) {
        return new GroupCollector<>(
                this.index,
                (group, _) -> {
                    @SuppressWarnings("unchecked")
                    final var pixels = (VirtualBuilding.Group<MindustryPixel>) group;
                    this.groups.add(pixels);
                    return true;
                },
                ArtKind.SORTER,
                _ -> true,
                isEligibleAnchor,
                _ -> true,
                range,
                _ -> null);
    }

    private void fill(final int w, final int h) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                this.index.insert(x, y, 1, new MindustryPixel(0xFFFFFFFF, null));
            }
        }
    }

    private static int ceil(final int a, final int b) {
        return (a + b - 1) / b;
    }
}
