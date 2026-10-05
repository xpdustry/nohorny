// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.util.Locale;

/// What happened to the image of a classification request.
public enum ImageState {
    /// The image was considered safe, it was never stored.
    NONE,
    /// The image file is stored.
    STORED,
    /// The image retention elapsed, the file was deleted.
    EXPIRED,
    /// The file was deleted by hand.
    PURGED;

    /// @return the lowercase name used by the API
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
