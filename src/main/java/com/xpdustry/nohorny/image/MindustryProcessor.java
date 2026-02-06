// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.image;

import com.xpdustry.nohorny.geometry.ImmutablePoint2;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record MindustryProcessor(
        List<DrawInstruction> instructions,
        List<ImmutablePoint2> links,
        @Nullable MindustryAuthor author) {}
