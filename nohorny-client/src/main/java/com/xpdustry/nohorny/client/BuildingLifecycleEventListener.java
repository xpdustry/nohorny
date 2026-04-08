// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.image.MindustryAuthor;
import mindustry.gen.Building;
import org.jspecify.annotations.Nullable;

interface BuildingLifecycleEventListener<B extends Building> {

    void onCreate(final B building, final @Nullable MindustryAuthor author);

    void onRemove(final int x, final int y, final int size);

    void onRemoveAll();
}
