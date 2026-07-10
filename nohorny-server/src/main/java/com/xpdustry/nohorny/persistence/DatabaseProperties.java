// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.database")
@Validated
public record DatabaseProperties(@NotNull Path path) {}
