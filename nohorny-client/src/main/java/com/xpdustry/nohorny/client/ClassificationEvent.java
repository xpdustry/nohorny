// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.classification.Rating;
import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.image.MindustryAuthor;
import com.xpdustry.nohorny.common.image.MindustryImage;
import org.jspecify.annotations.Nullable;

public record ClassificationEvent(
        VirtualBuilding.Group<? extends MindustryImage> group,
        Rating rating,
        @Nullable MindustryAuthor author) {}
