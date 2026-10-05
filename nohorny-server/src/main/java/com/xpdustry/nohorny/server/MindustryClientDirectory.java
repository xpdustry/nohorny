// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/// Identifies the origin of a classification request from its remote address: the loopback interface, a known public
/// Mindustry server, or an unknown host. The Mindustry server list is refreshed periodically from the official sources.
///
/// The list groups the servers by network, the network names are normalized with [#normalize].
@Component
public final class MindustryClientDirectory {

    private static final Logger log = LoggerFactory.getLogger(MindustryClientDirectory.class);

    private static final String[] MINDUSTRY_SERVER_SOURCES = {
        "https://raw.githubusercontent.com/Anuken/MindustryServerList/main/servers_v8.json",
        "https://raw.githubusercontent.com/Anuken/MindustryServerList/main/servers_be.json"
    };

    private final RestClient restClient;

    private volatile Map<InetAddress, Set<String>> addresses = Map.of();

    public MindustryClientDirectory(final RestClient restClient) {
        this.restClient = restClient;
    }

    // Also runs on startup, from the scheduler thread so it does not delay it
    @Scheduled(fixedDelayString = "${nohorny.mindustry.refresh-interval:10m}")
    public void refresh() {
        log.debug("Refreshing known Mindustry server list");

        final var refreshed = new HashMap<InetAddress, Set<String>>();
        for (final var source : MINDUSTRY_SERVER_SOURCES) {
            final @Nullable JsonNode groups;
            try {
                groups = this.restClient.get().uri(source).retrieve().body(JsonNode.class);
            } catch (final Exception exception) {
                log.error("Error while retrieving {}", source, exception);
                continue;
            }
            if (groups == null || !groups.isArray()) {
                continue;
            }

            for (final var group : groups) {
                final var name = normalize(group.path("name").asString());
                final var entries = group.path("address");
                if (!entries.isArray()) {
                    continue;
                }
                for (final var entry : entries) {
                    final var address = parse(entry.asString());
                    if (address == null) {
                        continue;
                    }
                    // A server without a usable network name is still a known server
                    final var names = refreshed.computeIfAbsent(address, ignored -> new TreeSet<>());
                    if (name != null) {
                        names.add(name);
                    }
                }
            }
        }

        log.debug("Refreshed known Mindustry server list, retrieved {} addresses", refreshed.size());
        final var copy = new HashMap<InetAddress, Set<String>>();
        refreshed.forEach((address, names) -> copy.put(address, Set.copyOf(names)));
        this.addresses = Map.copyOf(copy);
    }

    public ClientInfo whois(final String remoteAddress) {
        final InetAddress address;
        try {
            // Remote addresses are IP literals, no lookup happens
            address = InetAddress.getByName(remoteAddress);
        } catch (final UnknownHostException exception) {
            return new ClientInfo(ClientInfo.UNKNOWN, null);
        }
        if (address.isLoopbackAddress()) {
            return new ClientInfo(ClientInfo.LOCALHOST, null);
        }
        final var names = this.addresses.get(address);
        if (names != null) {
            return new ClientInfo(
                    ClientInfo.MINDUSTRY_SERVER, names.isEmpty() ? null : String.join(", ", new TreeSet<>(names)));
        }
        return new ClientInfo(ClientInfo.UNKNOWN, null);
    }

    /// @return the network name without its markup, see [MindustryText#plain], `null` if nothing is left
    static @Nullable String normalize(final String name) {
        final var plain = MindustryText.plain(name);
        return plain.isEmpty() ? null : plain;
    }

    private static @Nullable InetAddress parse(final String value) {
        try {
            return InetAddress.getByName(stripPort(value));
        } catch (final UnknownHostException ignored) {
            return null;
        }
    }

    private static String stripPort(final String address) {
        if (address.startsWith("[")) {
            final var closing = address.indexOf(']');
            return closing == -1 ? address : address.substring(1, closing);
        }
        final var firstColon = address.indexOf(':');
        final var lastColon = address.lastIndexOf(':');
        if (firstColon != -1 && firstColon == lastColon) {
            return address.substring(0, lastColon);
        }
        return address;
    }

    /// @param type `mindustry-server`, `localhost` or `unknown`
    /// @param network the normalized network names of a listed server, joined by commas, `null` for the other types
    ///     or when the listing has no usable name
    public record ClientInfo(String type, @Nullable String network) {
        public static final String MINDUSTRY_SERVER = "mindustry-server";
        public static final String LOCALHOST = "localhost";
        public static final String UNKNOWN = "unknown";
    }
}
