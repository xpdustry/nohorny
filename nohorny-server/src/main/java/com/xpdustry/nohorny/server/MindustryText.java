// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/// Reads the markup of Mindustry text, such as the server names, without the game.
///
/// The color tag parsing is ported from `MindustryDecoderImpl` of
/// [Distributor](https://github.com/xpdustry/distributor), relicensed under MIT by its author.
final class MindustryText {

    /// The color names of arc's `Colors`, then the ones Mindustry registers in `UI`.
    private static final Set<String> COLORS;

    static {
        final var names = Set.of(
                "CLEAR",
                "BLACK",
                "WHITE",
                "LIGHT_GRAY",
                "GRAY",
                "DARK_GRAY",
                "LIGHT_GREY",
                "GREY",
                "DARK_GREY",
                "BLUE",
                "NAVY",
                "ROYAL",
                "SLATE",
                "SKY",
                "CYAN",
                "TEAL",
                "GREEN",
                "ACID",
                "LIME",
                "FOREST",
                "OLIVE",
                "YELLOW",
                "GOLD",
                "GOLDENROD",
                "ORANGE",
                "BROWN",
                "TAN",
                "BRICK",
                "RED",
                "SCARLET",
                "CRIMSON",
                "CORAL",
                "SALMON",
                "PINK",
                "MAGENTA",
                "PURPLE",
                "VIOLET",
                "MAROON");
        final var colors = new HashSet<>(names);
        // Like arc, every name is also registered in lowercase without underscores
        for (final var name : names) {
            colors.add(name.toLowerCase(Locale.ROOT).replace("_", ""));
        }
        colors.addAll(Set.of("accent", "unlaunched", "highlight", "stat", "negstat"));
        COLORS = Set.copyOf(colors);
    }

    private MindustryText() {}

    /// Removes the color tags the way the game reads them: `[accent]`, `[#ff0000]` and the closing `[]` go away,
    /// `[[` is an escaped bracket, and an unknown tag such as `[abc]` stays as written.
    static String stripColors(final String input) {
        final var buffer = new StringBuilder(input.length());
        var index = 0;
        while (index < input.length()) {
            final var start = input.indexOf('[', index);
            if (start == -1) {
                buffer.append(input, index, input.length());
                break;
            }
            if (start + 1 < input.length() && input.charAt(start + 1) == '[') {
                buffer.append(input, index, start + 1);
                index = start + 2;
                continue;
            }
            final var close = input.indexOf(']', start);
            if (close == -1) {
                buffer.append(input, index, input.length());
                break;
            }
            final var value = input.substring(start + 1, close);
            if (value.isEmpty() || COLORS.contains(value) || isHexColor(value)) {
                buffer.append(input, index, start);
            } else {
                buffer.append(input, index, close + 1);
            }
            index = close + 1;
        }
        return buffer.toString();
    }

    /// Removes the color tags, replaces the icons of the game font and the invisible characters with spaces,
    /// then collapses the whitespace.
    ///
    /// @return the plain text, empty if nothing is left
    static String plain(final String input) {
        final var stripped = stripColors(input);
        final var buffer = new StringBuilder(stripped.length());
        var space = false;
        for (var index = 0; index < stripped.length(); ) {
            final var point = stripped.codePointAt(index);
            index += Character.charCount(point);
            if (Character.isWhitespace(point) || isInvisible(point)) {
                space = !buffer.isEmpty();
                continue;
            }
            if (space) {
                buffer.append(' ');
                space = false;
            }
            buffer.appendCodePoint(point);
        }
        return buffer.toString();
    }

    /// The icons of the game font are private use characters.
    private static boolean isInvisible(final int point) {
        final var type = Character.getType(point);
        return type == Character.PRIVATE_USE || type == Character.CONTROL || type == Character.FORMAT;
    }

    /// `#rgb` up to `#rrggbbaa`, except 7 digits which the game refuses.
    private static boolean isHexColor(final String value) {
        if (value.charAt(0) != '#') {
            return false;
        }
        final var digits = value.length() - 1;
        if (digits > 8 || digits == 7) {
            return false;
        }
        for (var i = 1; i < value.length(); i++) {
            final var ch = value.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}
