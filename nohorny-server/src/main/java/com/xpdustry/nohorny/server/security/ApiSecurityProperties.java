// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// @param apiDefaultPolicy whether the classification endpoint requires an account
/// @param admin the bootstrap administrator, created or reset on every startup
@ConfigurationProperties("nohorny.security")
@Validated
public record ApiSecurityProperties(
        @DefaultValue("ALLOW_ALL") @NotNull ApiDefaultPolicy apiDefaultPolicy,
        @DefaultValue @Valid Admin admin) {

    public enum ApiDefaultPolicy {
        ALLOW_ALL,
        DENY_ALL
    }

    /// @param username the username of the bootstrap administrator
    /// @param password its password, no bootstrap administrator is configured when blank
    public record Admin(
            @DefaultValue("admin") @Pattern(regexp = Usernames.PATTERN, message = Usernames.MESSAGE) String username,

            @Nullable String password) {

        public boolean configured() {
            return this.password != null && !this.password.isBlank() && !this.unresolved();
        }

        /// Spring keeps the placeholders it cannot resolve, such as `${NOHORNY_ADMIN_PASSWORD}` without the variable,
        /// which would otherwise become a well known password.
        public boolean unresolved() {
            return this.password != null && this.password.startsWith("${") && this.password.endsWith("}");
        }
    }
}
