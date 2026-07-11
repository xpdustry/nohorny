// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Identifies the origin of a classification request from its remote address: the loopback interface, a known public
 * Mindustry server, or an unknown host. The Mindustry server list is refreshed periodically from the official sources.
 */
@Component
public final class MindustryClientDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(MindustryClientDirectory.class);

    private static final String[] MINDUSTRY_SERVER_SOURCES = {
        "https://raw.githubusercontent.com/Anuken/MindustryServerList/main/servers_v8.json",
        "https://raw.githubusercontent.com/Anuken/MindustryServerList/main/servers_be.json"
    };

    private final RestClient restClient;

    private volatile Map<InetAddress, Set<String>> addresses = Map.of();

    public MindustryClientDirectory(final RestClient restClient) {
        this.restClient = restClient;
    }

    @PostConstruct
    void init() {
        this.refresh();
    }

    @Scheduled(fixedDelayString = "${nohorny.mindustry.refresh-interval:10m}")
    public void refresh() {
        LOGGER.info("Refreshing known Mindustry server list");

        final var refreshed = new ConcurrentHashMap<InetAddress, Set<String>>();
        for (final var source : MINDUSTRY_SERVER_SOURCES) {
            final @Nullable JsonNode groups;
            try {
                groups = this.restClient.get().uri(source).retrieve().body(JsonNode.class);
            } catch (final Exception exception) {
                LOGGER.error("Error while retrieving {}", source, exception);
                continue;
            }
            if (groups == null || !groups.isArray()) {
                continue;
            }

            for (final var group : groups) {
                final var name = group.path("name").asString();
                final var entries = group.path("address");
                if (!entries.isArray()) {
                    continue;
                }
                for (final var entry : entries) {
                    final var address = this.parse(entry.asString());
                    if (address != null) {
                        refreshed
                                .computeIfAbsent(address, ignored -> ConcurrentHashMap.newKeySet())
                                .add(name);
                    }
                }
            }
        }

        LOGGER.info("Refreshed known Mindustry server list, retrieved {} addresses", refreshed.size());
        this.addresses = Map.copyOf(refreshed);
    }

    public ClientInfo whois(final String remoteAddress) throws UnknownHostException {
        final var address = InetAddress.getByName(remoteAddress);
        if (address.isLoopbackAddress()) {
            return new ClientInfo("localhost", address.toString());
        }
        final var group = this.addresses.get(address);
        if (group != null) {
            return new ClientInfo(
                    "mindustry-server", new TreeSet<>(group).toString().replace(" ", ""));
        }
        return new ClientInfo("unknown", address.toString());
    }

    private @Nullable InetAddress parse(final String value) {
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

    public record ClientInfo(String type, String name) {}
}
