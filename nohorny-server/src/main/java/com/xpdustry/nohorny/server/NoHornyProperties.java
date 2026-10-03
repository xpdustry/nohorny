// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// @param publicUrl the public address of the server, used to link the request pages in the classification responses
@ConfigurationProperties("nohorny")
public record NoHornyProperties(@Nullable URI publicUrl) {}
