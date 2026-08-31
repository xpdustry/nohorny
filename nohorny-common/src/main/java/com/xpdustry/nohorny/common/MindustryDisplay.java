// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record MindustryDisplay(
        int resolution,
        Map<Integer, Processor> processors,
        @Nullable Tiled tiled) implements MindustryImage {

    public MindustryDisplay(final int resolution, final Map<Integer, Processor> processors) {
        this(resolution, processors, null);
    }

    public MindustryDisplay {
        NoHornyPreconditions.positive(resolution, "resolution");
        processors = Map.copyOf(processors);
    }

    public record Tiled(int frameSize) {}

    public record Processor(
            List<DrawInstruction> instructions, @Nullable MindustryAuthor author) {
        public Processor {
            instructions = List.copyOf(instructions);
        }
    }
}
