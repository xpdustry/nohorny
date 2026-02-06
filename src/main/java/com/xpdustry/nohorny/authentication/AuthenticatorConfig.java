// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.authentication;

import com.xpdustry.nohorny.config.SealedConfig;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.github.gestalt.config.annotations.Config;

@SealedConfig
public sealed interface AuthenticatorConfig {

    @SealedConfig(name = "allow-all")
    record AllowAll() implements AuthenticatorConfig {}

    @SealedConfig(name = "mindustry-server-list")
    record MindustryServerList(
            List<URI> sources, @Config(defaultVal = "1h") Duration refreshInterval) implements AuthenticatorConfig {}

    @SealedConfig(name = "api-key")
    record ApiKey() implements AuthenticatorConfig {}

    @SealedConfig(name = "localhost")
    record Localhost() implements AuthenticatorConfig {}
}
