// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;

@SuppressWarnings("UnusedReturnValue")
public final class NoHornyChecks {

    private NoHornyChecks() {}

    public static int within(final int value, final int min, final int max, final String name) {
        if (min > max) {
            throw new IllegalArgumentException("min must be lower or equal than max, got " + min + " and " + max);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be within [" + min + ", " + max + "], got " + value);
        }
        return value;
    }

    public static double greaterThan(final double value, final double minimumExclusive, final String name) {
        if (value <= minimumExclusive) {
            throw new IllegalArgumentException(name + " must be greater than " + minimumExclusive + ", got " + value);
        }
        return value;
    }

    public static int positive(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    public static Duration positive(final Duration value, final String name) {
        if (value.toMillis() < 0) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    public static String notBlank(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static <T extends Collection<?>> T notEmpty(final T value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }
}
