// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.plugin;

/// Whether the Discord alert should upload the classified image.
public enum DiscordWebhookImagePolicy {
    /// Attach the image only when the server does not expose a page for the request.
    AUTO,
    /// Always attach the image.
    ALWAYS,
    /// Never attach the image.
    NEVER,
}
