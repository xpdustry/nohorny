// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.geometry;

import java.util.ArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class VirtualBuildingIndexTest {

    @Test
    void test_ignore_occupied() {
        final var index = new VirtualBuildingIndex<Something>();
        Assertions.assertNotNull(index.insert(0, 0, 1, Something.INSTANCE));
        Assertions.assertNull(index.insert(0, 0, 1, Something.INSTANCE));
        Assertions.assertEquals(1, index.selectAll().size());
    }

    @Test
    void test_blocks_that_share_a_side() {
        final var index = new VirtualBuildingIndex<Something>();
        index.insert(0, 0, 1, Something.INSTANCE);
        index.insert(1, 0, 1, Something.INSTANCE);

        final var groups = new ArrayList<>(index.groups());
        Assertions.assertEquals(1, groups.size());

        final var group = groups.getFirst();
        Assertions.assertEquals(2, group.elements().size());
        Assertions.assertEquals(0, group.x());
        Assertions.assertEquals(0, group.y());
        Assertions.assertEquals(2, group.w());
        Assertions.assertEquals(1, group.h());
    }

    @Test
    void test_blocks_that_do_not_share_a_side() {
        final var index = new VirtualBuildingIndex<Something>();
        index.insert(2, 2, 2, Something.INSTANCE);
        index.insert(-2, 0, 1, Something.INSTANCE);
        index.insert(10, 10, 10, Something.INSTANCE);

        Assertions.assertEquals(3, index.groups().size());
    }

    @Test
    void test_blocks_that_partially_share_a_side() {
        final var index = new VirtualBuildingIndex<Something>();
        index.insert(1, 1, 2, Something.INSTANCE);
        index.insert(3, 2, 2, Something.INSTANCE);

        Assertions.assertEquals(1, index.groups().size());
    }

    @Test
    void test_blocks_that_only_share_a_corner() {
        final var index = new VirtualBuildingIndex<Something>();
        index.insert(0, 0, 1, Something.INSTANCE);
        index.insert(1, 1, 1, Something.INSTANCE);

        Assertions.assertEquals(2, index.groups().size());
    }

    @Test
    void test_block_remove() {
        final var index = new VirtualBuildingIndex<Something>();
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 5; y++) {
                index.insert(x, y, 1, Something.INSTANCE);
            }
        }

        var groups = new ArrayList<>(index.groups());
        Assertions.assertEquals(1, groups.size());
        Assertions.assertEquals(18, groups.getFirst().elements().size());

        index.remove(0, 1);
        index.remove(1, 1);

        groups = new ArrayList<>(index.groups());
        Assertions.assertEquals(1, groups.size());
        Assertions.assertEquals(16, groups.getFirst().elements().size());
    }

    @Test
    void test_block_remove_from_within() {
        final var index = new VirtualBuildingIndex<Something>();
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                index.insert(x, y, 1, Something.INSTANCE);
            }
        }

        var groups = new ArrayList<>(index.groups());
        Assertions.assertEquals(1, groups.size());
        Assertions.assertEquals(25, groups.getFirst().elements().size());

        // Removes a U shape inside the 5 by 5 square
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                if (x == 2 && (y == 2 || y == 3)) continue;
                index.remove(x, y);
            }
        }

        groups = new ArrayList<>(index.groups());
        Assertions.assertEquals(1, groups.size());
        Assertions.assertEquals(18, groups.getFirst().elements().size());
    }

    @Test
    void test_group_split() {
        final var index = new VirtualBuildingIndex<Something>();
        for (int x = 0; x <= 2; x++) {
            index.insert(x, 0, 1, Something.INSTANCE);
        }

        index.insert(1, 1, 1, Something.INSTANCE);
        Assertions.assertEquals(1, index.groups().size());

        index.remove(1, 0);
        Assertions.assertEquals(3, index.groups().size());
    }

    @Test
    void test_group_merge() {
        final var index = new VirtualBuildingIndex<Something>();
        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x <= 2; x++) {
                index.insert(x, y * 2, 1, Something.INSTANCE);
            }
        }

        Assertions.assertEquals(3, index.groups().size());

        index.insert(1, 1, 1, Something.INSTANCE);
        Assertions.assertEquals(2, index.groups().size());

        index.insert(1, 3, 1, Something.INSTANCE);
        Assertions.assertEquals(1, index.groups().size());
    }

    @Test
    void test_merge_big_blocks() {
        final var index = new VirtualBuildingIndex<Something>();
        index.insert(0, 0, 10, Something.INSTANCE);
        index.insert(10, 0, 10, Something.INSTANCE);
        index.insert(0, 10, 10, Something.INSTANCE);
        index.insert(10, 10, 10, Something.INSTANCE);

        final var groups = new ArrayList<>(index.groups());
        Assertions.assertEquals(1, groups.size());

        final var group = groups.getFirst();
        Assertions.assertEquals(4, group.elements().size());
        Assertions.assertEquals(0, group.x());
        Assertions.assertEquals(0, group.y());
        Assertions.assertEquals(20, group.w());
        Assertions.assertEquals(20, group.h());
    }

    @Test
    void test_group_on_same_axis_spaced_by_1() {
        final var index = new VirtualBuildingIndex<Something>();
        index.insert(0, 0, 6, Something.INSTANCE);
        index.insert(7, 0, 6, Something.INSTANCE);
        index.insert(0, 7, 6, Something.INSTANCE);
        index.insert(7, 7, 6, Something.INSTANCE);

        Assertions.assertEquals(4, index.groups().size());
    }

    @Test
    void test_select_all_within_square_uses_exclusive_bounds() {
        final var index = new VirtualBuildingIndex<Something>();
        index.insert(0, 0, 1, Something.INSTANCE);
        index.insert(1, 0, 1, Something.INSTANCE);

        Assertions.assertEquals(1, index.selectAllWithinSquare(0, 0, 1).size());
    }

    @Test
    void test_remove_all_within_square_clears_entire_building_occupancy() {
        final var index = new VirtualBuildingIndex<Something>();
        index.insert(0, 0, 2, Something.INSTANCE);

        Assertions.assertEquals(1, index.removeAllWithinSquare(0, 0, 1).size());
        Assertions.assertNull(index.select(1, 1));
        Assertions.assertTrue(index.selectAll().isEmpty());
    }

    @Test
    void test_remove_all() {
        final var index = new VirtualBuildingIndex<Something>();
        for (int x = 0; x < 5; x++) {
            index.insert(x, 0, 1, Something.INSTANCE);
        }

        var groups = new ArrayList<>(index.groups());
        Assertions.assertEquals(1, groups.size());
        Assertions.assertEquals(5, groups.getFirst().elements().size());

        index.removeAll();

        groups = new ArrayList<>(index.groups());
        Assertions.assertEquals(0, groups.size());
    }

    private enum Something {
        INSTANCE
    }
}
