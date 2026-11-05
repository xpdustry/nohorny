// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import org.jspecify.annotations.Nullable;

/// Narrows a list to the requests having a value, none, or whatever they have.
public sealed interface ValueFilter {

    default boolean isAny() {
        return this instanceof Any;
    }

    /// @return the value to match, `null` to match no value
    default @Nullable String value() {
        return null;
    }

    record Any() implements ValueFilter {}

    record None() implements ValueFilter {}

    record Named(String value) implements ValueFilter {}
}
