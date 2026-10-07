// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.util.List;

/// The classified requests per plugin version, over the windows of [History].
///
/// @param range the requested range, `24h`, `7d`, `30d` or `90d`
/// @param total every request of the range. The part not covered by the versions comes from the clients that do not
///     send one
/// @param versions the versions, the most used first
public record Versions(String range, long total, List<Count> versions) {}
