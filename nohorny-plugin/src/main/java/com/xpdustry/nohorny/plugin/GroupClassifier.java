// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.plugin;

import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;

@FunctionalInterface
interface GroupClassifier {

    // Queues the group for classification, returns false if the classifier is busy
    boolean tryAccept(final VirtualBuilding.Group<? extends MindustryImage> group);
}
