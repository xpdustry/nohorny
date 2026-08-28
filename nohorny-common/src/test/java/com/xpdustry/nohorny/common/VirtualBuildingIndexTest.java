// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Sloppy aaah tests
final class VirtualBuildingIndexTest {

    @Test
    void group_orthogonally_adjacent_buildings() {
        final var index = new VirtualBuildingIndex<String>();
        final var first = insert(index, 0, 0, 1, "first");
        final var second = insert(index, 1, 0, 1, "second");
        final var third = insert(index, 1, 1, 1, "third");

        final var grouper = index.startGrouperAt(0, 0, 10, 10);
        grouper.progress();

        assertTrue(grouper.isCompleted());
        final var group = grouper.create();
        assertNotNull(group);
        assertEquals(0, group.x());
        assertEquals(0, group.y());
        assertEquals(2, group.w());
        assertEquals(2, group.h());
        assertEquals(Set.of(first, second, third), Set.copyOf(group.elements()));
    }

    @Test
    void exclude_diagonally_adjacent_buildings() {
        final var index = new VirtualBuildingIndex<String>();
        final var first = insert(index, 0, 0, 1, "first");
        insert(index, 1, 1, 1, "diagonal");

        final var grouper = index.startGrouperAt(0, 0, 10, 10);
        grouper.progress();

        final var group = grouper.create();
        assertNotNull(group);
        assertEquals(Set.of(first), Set.copyOf(group.elements()));
    }

    @Test
    void limit_work_per_progress_call() {
        final var index = new VirtualBuildingIndex<String>();
        final var first = insert(index, 0, 0, 1, "first");
        final var second = insert(index, 1, 0, 1, "second");
        final var third = insert(index, 2, 0, 1, "third");
        final var grouper = index.startGrouperAt(0, 0, 10, 1);

        grouper.progress();

        assertFalse(grouper.isCompleted());
        assertTrue(grouper.isVisited(second.packed()));
        assertFalse(grouper.isVisited(third.packed()));
        final var firstProgress = grouper.create();
        assertNotNull(firstProgress);
        assertEquals(Set.of(first), Set.copyOf(firstProgress.elements()));

        grouper.progress();
        final var secondProgress = grouper.create();
        assertNotNull(secondProgress);
        assertEquals(Set.of(first, second), Set.copyOf(secondProgress.elements()));

        grouper.progress();
        assertTrue(grouper.isCompleted());
        final var completed = grouper.create();
        assertNotNull(completed);
        assertEquals(Set.of(first, second, third), Set.copyOf(completed.elements()));
    }

    @Test
    void exclude_connected_buildings_outside_range() {
        final var index = new VirtualBuildingIndex<String>();
        final var first = insert(index, 0, 0, 1, "first");
        final var second = insert(index, 1, 0, 1, "inside");
        final var outside = insert(index, 2, 0, 1, "outside");

        final var grouper = index.startGrouperAt(0, 0, 2, 10);
        grouper.progress();

        assertTrue(grouper.isCompleted());
        assertFalse(grouper.isVisited(outside.packed()));
        final var group = grouper.create();
        assertNotNull(group);
        assertEquals(Set.of(first, second), Set.copyOf(group.elements()));
    }

    @Test
    void update_connections_after_removal_and_reinsertion() {
        final var index = new VirtualBuildingIndex<String>();
        final var first = insert(index, 0, 0, 1, "first");
        final var removed = insert(index, 1, 0, 1, "removed");
        final var third = insert(index, 2, 0, 1, "third");

        assertEquals(removed, index.remove(1, 0));

        final var disconnectedGrouper = index.startGrouperAt(0, 0, 10, 10);
        disconnectedGrouper.progress();
        final var disconnected = disconnectedGrouper.create();
        assertNotNull(disconnected);
        assertEquals(Set.of(first), Set.copyOf(disconnected.elements()));

        final var replacement = insert(index, 1, 0, 1, "replacement");
        final var reconnectedGrouper = index.startGrouperAt(0, 0, 10, 10);
        reconnectedGrouper.progress();
        final var reconnected = reconnectedGrouper.create();
        assertNotNull(reconnected);
        assertEquals(Set.of(first, replacement, third), Set.copyOf(reconnected.elements()));
    }

    @Test
    void calculate_bounds_for_different_building_sizes() {
        final var index = new VirtualBuildingIndex<String>();
        final var first = insert(index, -2, -1, 2, "first");
        final var second = insert(index, 0, -1, 3, "second");

        final var grouper = index.startGrouperAt(-1, 0, 10, 10);
        grouper.progress();

        final var group = grouper.create();
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
        final var index = new VirtualBuildingIndex<String>();

        final var grouper = index.startGrouperAt(4, 7, 10, 10);
        grouper.progress();

        assertTrue(grouper.isCompleted());
        assertNull(grouper.create());
    }

    private static <T> VirtualBuilding<T> insert(
            final VirtualBuildingIndex<T> index, final int x, final int y, final int size, final T data) {
        final var building = index.insert(x, y, size, data);
        assertNotNull(building);
        return building;
    }
}
