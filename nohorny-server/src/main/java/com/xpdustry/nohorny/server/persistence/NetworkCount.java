// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// The number of requests sent by the servers of a listed Mindustry network.
public interface NetworkCount {

    /// @return the normalized network name
    String getNetwork();

    long getTotal();
}
