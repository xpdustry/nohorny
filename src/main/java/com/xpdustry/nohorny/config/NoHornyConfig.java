// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.config;

import com.xpdustry.nohorny.authentication.AuthenticatorConfig;
import com.xpdustry.nohorny.classification.ClassifierConfig;
import com.xpdustry.nohorny.tracking.AutoModeratorConfig;
import com.xpdustry.nohorny.tracking.CanvasTrackerConfig;
import com.xpdustry.nohorny.tracking.DisplayTrackerConfig;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.github.gestalt.config.annotations.Config;

public record NoHornyConfig(Optional<ServerConfig> server, Optional<ClientConfig> client) {

    public record ServerConfig(
            @Config(defaultVal = "127.0.0.1") InetAddress host,
            @Config(defaultVal = "8080") int port,
            List<ClassifierConfig> classifiers,
            List<AuthenticatorConfig> authenticators) {}

    public record ClientConfig(
            @Config(defaultVal = "https://nohorny.xpdustry.com")
            URI endpoint,

            Optional<String> token,
            @Config(defaultVal = "10s") Duration timeout,
            DisplayTrackerConfig displays,
            CanvasTrackerConfig canvases,
            AutoModeratorConfig autoMod) {}
}
