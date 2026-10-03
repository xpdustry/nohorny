// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.util.Locale;

/// What happened to the image of a classification request.
public enum ImageState {
    /// The image was considered safe, it was never stored.
    NONE,
    /// The image bytes are stored.
    STORED,
    /// The image retention elapsed, the bytes were deleted.
    EXPIRED,
    /// The bytes were deleted by hand.
    PURGED;

    /// @return the lowercase name used by the API
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
