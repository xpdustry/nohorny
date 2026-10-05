// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// Where the server keeps its data.
///
/// @param database the SQLite database file
/// @param images the directory of the stored images, see [ImageStore]
@ConfigurationProperties("nohorny.storage")
@Validated
public record StorageProperties(
        @DefaultValue("database.sqlite") @NotNull Path database,
        @DefaultValue("images") @NotNull Path images) {}
