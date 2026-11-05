// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.util.Locale;
import java.util.Optional;

public enum ArtKind {
    LOGIC_DISPLAY("Logic display"),
    CANVAS("Canvas"),
    SORTER("Sorter"),
    ILLUMINATOR("Illuminator");

    public static final String HEADER = "X-NoHorny-Art-Kind";

    private final String label;

    ArtKind(final String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<ArtKind> fromKey(final String key) {
        for (final var kind : values()) {
            if (kind.key().equals(key)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
