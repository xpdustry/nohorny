// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import org.jspecify.annotations.Nullable;

public record ClassificationEvent(
        VirtualBuilding.Group<? extends MindustryImage> group,
        @Nullable MindustryAuthor author,
        ClassificationResponse response) {}
