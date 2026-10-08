// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.server.persistence.Requester;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/// Identifies the requester of a classification, shared by the rate limits and the recorded requests so both always
/// agree on who sent it.
@Component
public final class RequesterResolver {

    private final MindustryClientDirectory clients;

    public RequesterResolver(final MindustryClientDirectory clients) {
        this.clients = clients;
    }

    /// @param authentication the caller, `null` or anonymous when it did not authenticate
    /// @param address the client address
    /// @return an account first, then a listed Mindustry server or the loopback interface, then an anonymous client
    public Requester resolve(final @Nullable Authentication authentication, final String address) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return Requester.user(authentication.getName());
        }
        final var client = this.clients.whois(address);
        return switch (client.type()) {
            case MindustryClientDirectory.ClientInfo.MINDUSTRY_SERVER -> Requester.mindustryNetwork(client.network());
            case MindustryClientDirectory.ClientInfo.LOCALHOST -> Requester.localhost();
            default -> Requester.anonymous();
        };
    }
}
