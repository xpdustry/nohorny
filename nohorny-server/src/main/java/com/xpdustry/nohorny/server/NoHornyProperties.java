// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nohorny")
public record NoHornyProperties(@Nullable URI publicUrl) {}
