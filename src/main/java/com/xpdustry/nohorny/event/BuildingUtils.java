// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.event;

import mindustry.gen.Building;

public final class BuildingUtils {

    private BuildingUtils() {}

    public static int anchorTileX(final Building building) {
        return building.tileX() - building.block.sizeOffset;
    }

    public static int anchorTileY(final Building building) {
        return building.tileY() - building.block.sizeOffset;
    }
}
