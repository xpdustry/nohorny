// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.util.List;

/// @param total every request of the range. The part not covered by the versions comes from the clients that do not
///     send one
public record Versions(String range, long total, List<Count> versions) {}
