// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record MindustryDisplay(int resolution, Map<Integer, Processor> processors) implements MindustryImage {
    public MindustryDisplay {
        NoHornyPreconditions.positive(resolution, "resolution");
        processors = Int2ObjectMaps.unmodifiable(new Int2ObjectOpenHashMap<>(processors));
    }

    public record Processor(
            List<DrawInstruction> instructions, @Nullable MindustryAuthor author) {
        public Processor {
            instructions = List.copyOf(instructions);
        }
    }
}
