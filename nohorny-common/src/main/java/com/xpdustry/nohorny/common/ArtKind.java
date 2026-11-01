// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.util.Locale;
import java.util.Optional;

/// The kind of buildings an image was made with, sent along the image so the moderators know what to look for.
public enum ArtKind {
    LOGIC_DISPLAY("Logic display"),
    CANVAS("Canvas"),
    SORTER("Sorter"),
    ILLUMINATOR("Illuminator");

    /// The header carrying the kind of a classified image, see [#key()].
    public static final String HEADER = "X-NoHorny-Art-Kind";

    private final String label;

    ArtKind(final String label) {
        this.label = label;
    }

    /// @return the human readable name of the kind
    public String label() {
        return this.label;
    }

    /// @return the lowercase name used by the API, like `logic-display`
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
