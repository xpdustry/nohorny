// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// The number of requests of a named [RequesterType#MINDUSTRY_NETWORK] requester.
public interface NetworkCount {

    /// @return the normalized network name
    String getNetwork();

    long getTotal();
}
