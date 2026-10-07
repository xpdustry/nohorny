// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

/// The requests of a range sharing a name, like a Mindustry network or a plugin version.
public record Count(String name, long count) {}
