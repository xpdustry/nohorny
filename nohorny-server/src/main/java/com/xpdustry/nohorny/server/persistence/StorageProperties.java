// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.storage")
@Validated
public record StorageProperties(
        @DefaultValue("database.sqlite") @NotNull Path database,
        @DefaultValue("images") @NotNull Path images) {}
