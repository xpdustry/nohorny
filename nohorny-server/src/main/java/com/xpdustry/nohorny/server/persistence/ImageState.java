// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.util.Locale;

public enum ImageState {
    /// The image was considered safe, it was never stored.
    NONE,
    STORED,
    /// The image retention elapsed, the file was deleted.
    EXPIRED,
    /// The file was deleted from the request page or the admin panel.
    DELETED;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
