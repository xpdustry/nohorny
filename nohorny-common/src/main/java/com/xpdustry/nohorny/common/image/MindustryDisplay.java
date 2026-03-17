// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.image;

import com.xpdustry.nohorny.common.NoHornyChecks;
import com.xpdustry.nohorny.common.geometry.ImmutablePoint2;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record MindustryDisplay(int resolution, Map<ImmutablePoint2, Processor> processors) implements MindustryImage {
    public MindustryDisplay {
        NoHornyChecks.positive(resolution, "resolution");
    }

    public record Processor(
            List<DrawInstruction> instructions, @Nullable MindustryAuthor author) {}
}
