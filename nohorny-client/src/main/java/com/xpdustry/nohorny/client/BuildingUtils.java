// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import mindustry.gen.Building;

final class BuildingUtils {

    public static int anchorTileX(final Building building) {
        return building.tileX() - building.block.sizeOffset;
    }

    public static int anchorTileY(final Building building) {
        return building.tileY() - building.block.sizeOffset;
    }

    private BuildingUtils() {}
}
