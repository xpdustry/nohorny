// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.tracking;

import com.xpdustry.nohorny.classification.Classification;
import com.xpdustry.nohorny.geometry.VirtualBuilding;
import com.xpdustry.nohorny.image.MindustryAuthor;
import com.xpdustry.nohorny.image.MindustryCanvas;
import com.xpdustry.nohorny.image.MindustryDisplay;
import com.xpdustry.nohorny.image.MindustryImage;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import org.jspecify.annotations.Nullable;

public record ClassificationEvent(
        Classification classification,
        VirtualBuilding.Group<? extends MindustryImage> group,
        @Nullable MindustryAuthor author) {

    public ClassificationEvent(
            final Classification classification, final VirtualBuilding.Group<? extends MindustryImage> group) {
        this(classification, group, computeAuthor(group));
    }

    @SuppressWarnings("NullAway")
    private static @Nullable MindustryAuthor computeAuthor(
            final VirtualBuilding.Group<? extends MindustryImage> group) {
        final var authors = new ArrayList<MindustryAuthor>();
        var total = 0;
        for (final var element : group.elements()) {
            switch (element.data()) {
                case MindustryCanvas canvas -> {
                    total++;
                    if (canvas.author() != null) {
                        authors.add(canvas.author());
                    }
                }
                case MindustryDisplay display -> {
                    for (final var processor : display.processors().values()) {
                        total++;
                        if (processor.author() != null) {
                            authors.add(processor.author());
                        }
                    }
                }
            }
        }

        if (authors.isEmpty() || (float) authors.size() / total < 0.4F) {
            return null;
        }

        final var counts = new HashMap<InetAddress, Integer>();
        MindustryAuthor best = null;
        for (final var author : authors) {
            final var address = author.address();
            counts.compute(address, (_, v) -> v == null ? 1 : v + 1);
            if (best == null || counts.get(address) > counts.get(best.address())) {
                best = author;
            }
        }

        return best;
    }
}
