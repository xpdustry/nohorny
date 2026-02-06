// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.event;

import mindustry.gen.Building;
import mindustry.gen.Player;
import org.jspecify.annotations.Nullable;

public interface BuildingLifecycleEventListener<B extends Building> {

    void onCreate(final B building, final @Nullable Player player);

    void onRemove(final int x, final int y, final int size);

    void onRemoveAll();
}
