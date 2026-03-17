// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import mindustry.gen.Building;
import mindustry.gen.Player;
import org.jspecify.annotations.Nullable;

interface BuildingLifecycleEventListener<B extends Building> {

    // TODO Replace Player with MindustryAuthor
    void onCreate(final B building, final @Nullable Player player);

    void onRemove(final int x, final int y, final int size);

    void onRemoveAll();
}
