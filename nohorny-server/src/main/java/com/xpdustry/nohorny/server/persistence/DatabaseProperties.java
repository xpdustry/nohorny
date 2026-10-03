// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// @param path the SQLite database file
@ConfigurationProperties("nohorny.database")
@Validated
public record DatabaseProperties(
        @DefaultValue("database.sqlite") @NotNull Path path) {}
