// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.VirtualBuilding;
import org.jspecify.annotations.Nullable;

public record ClassificationEvent(
        VirtualBuilding.Group<? extends MindustryImage> group,
        Rating rating,
        @Nullable MindustryAuthor author) {}
