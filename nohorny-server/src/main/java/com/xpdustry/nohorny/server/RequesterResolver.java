// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.server.mapping.AddressRange;
import com.xpdustry.nohorny.server.mapping.RequesterMappingService;
import com.xpdustry.nohorny.server.persistence.Requester;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class RequesterResolver {

    private final MindustryClientDirectory clients;
    private final RequesterMappingService mappings;

    public RequesterResolver(final MindustryClientDirectory clients, final RequesterMappingService mappings) {
        this.clients = clients;
        this.mappings = mappings;
    }

    public Requester resolve(final @Nullable Authentication authentication, final String address) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return Requester.user(authentication.getName());
        }
        final var parsed = AddressRange.parseRemoteAddress(address);
        if (parsed == null) {
            return Requester.anonymous();
        }
        final var mapped = this.mappings.find(parsed);
        if (mapped.isPresent()) {
            return mapped.get();
        }
        final var client = this.clients.whois(parsed);
        return switch (client.type()) {
            case MINDUSTRY_SERVER ->
                Requester.mindustryNetwork(client.networks().isEmpty() ? null : String.join(", ", client.networks()));
            case LOCAL -> Requester.local();
            case UNKNOWN -> Requester.anonymous();
        };
    }
}
