// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// The number of requests sharing a name, like a Mindustry network or a plugin version.
public interface NamedCount {

    String getName();

    long getTotal();
}
