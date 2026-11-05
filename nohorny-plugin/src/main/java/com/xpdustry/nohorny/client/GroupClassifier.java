// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.ArtKind;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;

@FunctionalInterface
interface GroupClassifier {

    boolean tryAccept(final VirtualBuilding.Group<? extends MindustryImage> group, final ArtKind kind);
}
