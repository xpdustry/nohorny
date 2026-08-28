// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

// Sloppy aaah tests
final class VirtualBuildingIndexTest {

    @Test
    void group_orthogonally_adjacent_buildings() {
        final var index = VirtualBuildingIndex2.<String>create(6);
        final var first = insert(index.withLiveView(), 0, 0, 1, "first");
        final var second = insert(index.withLiveView(), 1, 0, 1, "second");
        final var third = insert(index.withLiveView(), 1, 1, 1, "third");

        final var group = index.withBaseView().selectGroupWithinRange(0, 0, 10);
        assertNotNull(group);
        assertEquals(0, group.x());
        assertEquals(0, group.y());
        assertEquals(2, group.w());
        assertEquals(2, group.h());
        assertEquals(Set.of(first, second, third), Set.copyOf(group.elements()));
    }

    @Test
    void exclude_diagonally_adjacent_buildings() {
        final var index = VirtualBuildingIndex2.<String>create(6);
        final var first = insert(index.withLiveView(), 0, 0, 1, "first");
        insert(index.withLiveView(), 1, 1, 1, "diagonal");

        final var group = index.withBaseView().selectGroupWithinRange(0, 0, 10);
        assertNotNull(group);
        assertEquals(Set.of(first), Set.copyOf(group.elements()));
    }

    @Test
    void exclude_connected_buildings_outside_range() {
        final var index = VirtualBuildingIndex2.<String>create(6);
        final var first = insert(index.withLiveView(), 0, 0, 1, "first");
        final var second = insert(index.withLiveView(), 1, 0, 1, "inside");
        final var outside = insert(index.withLiveView(), 2, 0, 1, "outside");

        final var group = index.withBaseView().selectGroupWithinRange(0, 0, 2);
        assertNotNull(group);
        assertEquals(Set.of(first, second), Set.copyOf(group.elements()));
        assertEquals(outside, index.withLiveView().select(2, 0));
    }

    @Test
    void update_connections_after_removal_and_reinsertion() {
        final var index = VirtualBuildingIndex2.<String>create(6);
        final var first = insert(index.withLiveView(), 0, 0, 1, "first");
        final var removed = insert(index.withLiveView(), 1, 0, 1, "removed");
        final var third = insert(index.withLiveView(), 2, 0, 1, "third");

        assertEquals(removed, index.withLiveView().remove(1, 0));

        final var disconnected = index.withBaseView().selectGroupWithinRange(0, 0, 10);
        assertNotNull(disconnected);
        assertEquals(Set.of(first), Set.copyOf(disconnected.elements()));

        final var replacement = insert(index.withLiveView(), 1, 0, 1, "replacement");
        final var reconnected = index.withBaseView().selectGroupWithinRange(0, 0, 10);
        assertNotNull(reconnected);
        assertEquals(Set.of(first, replacement, third), Set.copyOf(reconnected.elements()));
    }

    @Test
    void calculate_bounds_for_different_building_sizes() {
        final var index = VirtualBuildingIndex2.<String>create(6);
        final var first = insert(index.withLiveView(), -2, -1, 2, "first");
        final var second = insert(index.withLiveView(), 0, -1, 3, "second");

        final var group = index.withBaseView().selectGroupWithinRange(-1, 0, 10);
        assertNotNull(group);
        assertEquals(-2, group.x());
        assertEquals(-1, group.y());
        assertEquals(5, group.w());
        assertEquals(3, group.h());
        assertEquals(Set.of(first, second), Set.copyOf(group.elements()));
        assertEquals(2, group.elements().size());
    }

    @Test
    void complete_without_a_group_when_start_is_empty() {
        final var index = VirtualBuildingIndex2.<String>create(6);

        assertNull(index.withBaseView().selectGroupWithinRange(4, 7, 10));
    }

    private static <T> VirtualBuilding<T> insert(
            final VirtualBuildingIndex2.LiveView<T> index, final int x, final int y, final int size, final T data) {
        final var building = index.insert(x, y, size, data);
        assertNotNull(building);
        return building;
    }
}
