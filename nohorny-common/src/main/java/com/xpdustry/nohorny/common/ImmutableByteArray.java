// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.util.Arrays;
import java.util.stream.IntStream;

public final class ImmutableByteArray {

    private static final ImmutableByteArray EMPTY = new ImmutableByteArray(new byte[0]);

    public static ImmutableByteArray empty() {
        return EMPTY;
    }

    public static ImmutableByteArray wrap(final byte... values) {
        return values.length == 0 ? EMPTY : new ImmutableByteArray(values);
    }

    public static ImmutableByteArray copyOf(final byte[] values) {
        return values.length == 0 ? EMPTY : new ImmutableByteArray(Arrays.copyOf(values, values.length));
    }

    private final byte[] array;

    private ImmutableByteArray(final byte[] array) {
        this.array = array;
    }

    public int length() {
        return this.array.length;
    }

    public boolean isEmpty() {
        return this.array.length == 0;
    }

    public byte get(final int index) {
        return this.array[index];
    }

    public byte[] array() {
        return this.array.clone();
    }

    public IntStream stream() {
        return IntStream.range(0, array.length).map(i -> array[i]);
    }

    @Override
    public boolean equals(final Object other) {
        return (this == other || (other instanceof ImmutableByteArray that && Arrays.equals(this.array, that.array)));
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
