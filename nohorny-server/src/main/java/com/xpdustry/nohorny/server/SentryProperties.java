// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// @param dsn the Sentry project receiving the errors of the pages, nothing is reported without it
@ConfigurationProperties("nohorny.sentry")
public record SentryProperties(@Nullable String dsn) {}
