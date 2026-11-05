// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record RequestPage(List<RequestView> items, @Nullable String nextCursor) {}
