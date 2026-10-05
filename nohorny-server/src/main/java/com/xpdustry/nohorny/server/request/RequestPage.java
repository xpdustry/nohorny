// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// @param nextCursor the `before` parameter of the next page, `null` on the last page
public record RequestPage(List<RequestView> items, @Nullable String nextCursor) {}
