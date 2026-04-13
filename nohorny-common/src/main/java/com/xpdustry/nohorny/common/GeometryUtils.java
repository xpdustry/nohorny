// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

public final class GeometryUtils {

    public static int pack(final int x, final int y) {
        NoHornyChecks.within(x, Short.MIN_VALUE, Short.MAX_VALUE, "x");
        NoHornyChecks.within(y, Short.MIN_VALUE, Short.MAX_VALUE, "y");
        return (Short.toUnsignedInt((short) x) << Short.SIZE) | Short.toUnsignedInt((short) y);
    }

    public static short x(final int packed) {
        return (short) (packed >>> Short.SIZE);
    }

    public static short y(final int packed) {
        return (short) packed;
    }

    private GeometryUtils() {}
}
