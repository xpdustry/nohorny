// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.classification;

public enum Rating {
    NSFW,
    WARN,
    SAFE;

    @SuppressWarnings("EnumOrdinal") // I know what I am doing, trust...
    public boolean isWorseOrEqualThan(final Rating that) {
        return this.ordinal() <= that.ordinal();
    }
}
