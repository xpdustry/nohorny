// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.status")
@Validated
public record StatusProperties(@NotBlank String motd) {}
