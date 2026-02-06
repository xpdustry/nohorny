// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.image;

import com.xpdustry.nohorny.struct.ImmutableByteArray;
import com.xpdustry.nohorny.struct.ImmutableIntArray;
import org.jspecify.annotations.Nullable;

public record MindustryCanvas(
        int resolution,
        ImmutableIntArray palette,
        ImmutableByteArray pixels,
        @Nullable MindustryAuthor author) implements MindustryImage {}
