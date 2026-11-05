// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nohorny.sentry")
public record SentryProperties(@Nullable String dsn) {}
