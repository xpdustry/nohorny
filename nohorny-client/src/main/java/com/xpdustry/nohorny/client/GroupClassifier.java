// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;

@FunctionalInterface
interface GroupClassifier {

    // Called from the main thread, so the actual classification must happen elsewhere.
    // Returns false when busy, the group will be offered again on the next tick.
    boolean tryAccept(final VirtualBuilding.Group<? extends MindustryImage> group);
}
