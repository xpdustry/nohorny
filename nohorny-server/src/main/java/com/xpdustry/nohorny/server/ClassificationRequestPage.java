// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ClassificationRequestPage(
        List<ClassificationRequestView> items, @Nullable Long nextCursor) {}
