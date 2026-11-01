// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.ArtKind;
import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import org.jspecify.annotations.Nullable;

/// Event fired when a tracked canvas or display group has been classified.
///
/// @param group the classified buildings
/// @param kind the kind of buildings the group is made of
/// @param author the player associated with the buildings, or `null` when unknown
/// @param response the classification result
public record ClassificationEvent(
        VirtualBuilding.Group<? extends MindustryImage> group,
        ArtKind kind,
        @Nullable MindustryAuthor author,
        ClassificationResponse response) {}
