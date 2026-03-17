// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import com.xpdustry.nohorny.common.lifecycle.LifecycleListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KnowYourClient implements LifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowYourClient.class);
    private static final TypeToken<List<KnowYourClient.MindustryServerGroup>> SERVER_LIST_TYPE_TOKEN =
            new TypeToken<>() {};

    private static final List<URI> MINDUSTRY_SERVER_SOURCES = List.of(
            URI.create("https://github.com/Anuken/MindustryServerList/blob/main/servers_v8.json"),
            URI.create("https://github.com/Anuken/MindustryServerList/blob/main/servers_be.json"));

    private final HttpClient http;
    private final Gson gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(InetAddress.class, new ServerAddressAdapter())
            .create();

    private volatile Map<InetAddress, Set<String>> addresses = Map.of();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("MindustryServerAuthenticator-refresh").factory());

    public record Info(String type, String name) {}

    public KnowYourClient(final HttpClient http) {
        this.http = http;
    }

    public Info whois(final HttpExchange exchange) {
        final var address = exchange.getRemoteAddress().getAddress();
        if (address.isLoopbackAddress()) {
            return new Info("localhost", address.toString());
        }
        final var group = this.addresses.get(address);
        if (group != null) {
            return new Info("mindustry-server", group.stream().collect(Collectors.joining(",", "[", "]")));
        }
        return new Info("unknown", address.toString());
    }

    @Override
    public void onInit() {
        this.scheduler.scheduleAtFixedRate(this::refresh, 0, 10, TimeUnit.MINUTES);
    }

    @Override
    public void onExit() {
        this.scheduler.shutdown();
    }

    public void refresh() {
        LOGGER.info("Refreshing allowed server list");

        final var addresses = new HashMap<InetAddress, Set<String>>();
        for (final var source : MINDUSTRY_SERVER_SOURCES) {
            final HttpResponse<String> response;
            try {
                response = this.http.send(
                        HttpRequest.newBuilder(source)
                                .GET()
                                .timeout(Duration.ofSeconds(5))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (final IOException e) {
                LOGGER.error("Error while retrieving {}", source, e);
                continue;
            }
            if (response.statusCode() != 200) {
                LOGGER.error("Error while retrieving {}, got http code {}", source, response.statusCode());
                continue;
            }

            final var instant = System.currentTimeMillis();
            final List<MindustryServerGroup> groups;
            try {
                groups = this.gson.fromJson(response.body(), SERVER_LIST_TYPE_TOKEN);
            } catch (final Exception e) {
                LOGGER.error("Error while parsing {}", source, e);
                continue;
            }
            LOGGER.debug("Took {} milliseconds for parsing {}", System.currentTimeMillis() - instant, source);

            for (final var group : groups) {
                for (final var address : group.addresses) {
                    if (address != null) {
                        addresses.computeIfAbsent(address, _ -> new HashSet<>()).add(group.name);
                    }
                }
            }
        }

        LOGGER.info("Successfully refreshed allowed server list, retrieved {} addresses", addresses.size());
        this.addresses = addresses;
    }

    private record MindustryServerGroup(
            String name, @SerializedName("address") Set<@Nullable InetAddress> addresses) {}

    private static final class ServerAddressAdapter extends TypeAdapter<InetAddress> {

        @SuppressWarnings("EmptyCatch")
        @Override
        public @Nullable InetAddress read(final JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            try {
                return InetAddress.getByName(ServerAddressAdapter.stripPort(in.nextString()));
            } catch (final UnknownHostException _) {
                return null;
            }
        }

        @Override
        public void write(final JsonWriter out, final @Nullable InetAddress value) {
            throw new UnsupportedOperationException();
        }

        private static String stripPort(final String address) {
            // IPV6 uses brackets if ports are present
            if (address.startsWith("[")) {
                final var closing = address.indexOf(']');
                return closing == -1 ? address : address.substring(1, closing);
            }
            // IPV4 will contain only one colon if there's a port
            final var firstColonIndex = address.indexOf(':');
            final var lastColonIndex = address.lastIndexOf(':');
            if (firstColonIndex != -1 && firstColonIndex == lastColonIndex) {
                return address.substring(0, lastColonIndex);
            }
            return address;
        }
    }
}
