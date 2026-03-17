// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.struct;

import java.util.Arrays;
import java.util.stream.IntStream;

public final class ImmutableIntArray {

    private static final ImmutableIntArray EMPTY = new ImmutableIntArray(new int[0]);

    public static ImmutableIntArray empty() {
        return EMPTY;
    }

    public static ImmutableIntArray wrap(final int... values) {
        return values.length == 0 ? EMPTY : new ImmutableIntArray(values);
    }

    public static ImmutableIntArray copyOf(final int[] values) {
        return values.length == 0 ? EMPTY : new ImmutableIntArray(Arrays.copyOf(values, values.length));
    }

    private final int[] array;

    private ImmutableIntArray(final int[] array) {
        this.array = array;
    }

    public int length() {
        return this.array.length;
    }

    public boolean isEmpty() {
        return this.array.length == 0;
    }

    public int get(final int index) {
        return this.array[index];
    }

    public int[] array() {
        return this.array.clone();
    }

    public IntStream stream() {
        return Arrays.stream(this.array);
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || (other instanceof ImmutableIntArray that && Arrays.equals(this.array, that.array));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.array);
    }

    @Override
    public String toString() {
        return Arrays.toString(this.array);
    }
}
