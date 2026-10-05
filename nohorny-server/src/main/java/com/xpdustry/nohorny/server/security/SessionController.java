// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.security;

import com.xpdustry.nohorny.server.user.UserService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SessionController {

    private final UserService users;

    public SessionController(final UserService users) {
        this.users = users;
    }

    /// Only reached by authenticated callers, see [SecurityConfiguration].
    @GetMapping(path = "/api/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public Session onSession(final Authentication authentication) {
        return new Session(
                authentication.getName(),
                SecurityConfiguration.isAdmin(authentication),
                this.users.isBootstrap(authentication.getName()));
    }

    /// @param bootstrap whether the caller is the bootstrap administrator
    public record Session(String username, boolean admin, boolean bootstrap) {}
}
