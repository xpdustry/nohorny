// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("nohorny.security")
@Validated
public record ApiSecurityProperties(@NotNull ApiDefaultPolicy apiDefaultPolicy) {
    public enum ApiDefaultPolicy {
        ALLOW_ALL,
        DENY_ALL
    }
}
