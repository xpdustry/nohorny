// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.authentication;

import com.github.mizosoft.methanol.Methanol;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MindustryServerAuthenticator implements Authenticator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MindustryServerAuthenticator.class);
    private static final TypeToken<List<MindustryServerGroup>> SERVER_LIST_TYPE_TOKEN = new TypeToken<>() {};

    private final Methanol http;
    private final Gson gson;
    private final List<URI> sources;
    private final ScheduledExecutorService executor;
    private final Duration refreshInterval;
    private volatile Map<InetAddress, Set<String>> addresses = Map.of();
    private @Nullable ScheduledFuture<?> refreshTask;

    public MindustryServerAuthenticator(
            final Methanol http,
            final Gson gson,
            final List<URI> sources,
            final Duration refreshInterval,
            final ScheduledExecutorService executor) {
        if (refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalArgumentException("refreshInterval must be positive");
        }
        this.http = http;
        this.gson = gson;
        this.sources = List.copyOf(sources);
        this.refreshInterval = refreshInterval;
        this.executor = executor;
    }

    @Override
    public void onInit() {
        this.refresh();
        this.refreshTask = this.executor.scheduleAtFixedRate(
                this::refresh, this.refreshInterval.toMillis(), this.refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void onExit() {
        if (this.refreshTask != null) {
            this.refreshTask.cancel(true);
            this.refreshTask = null;
        }
    }

    public void refresh() {
        LOGGER.info("Refreshing allowed server list");

        final var addresses = new HashMap<InetAddress, Set<String>>();
        for (final var source : this.sources) {
            final HttpResponse<String> response;
            try {
                response = this.http.send(
                        HttpRequest.newBuilder(source).GET().build(), HttpResponse.BodyHandlers.ofString());
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
            if (groups == null) {
                LOGGER.error("Error while parsing {}, got an empty payload", source);
                continue;
            }
            LOGGER.debug("Took {} milliseconds for parsing {}", System.currentTimeMillis() - instant, source);

            for (final var group : groups) {
                for (final var address : group.addresses) {
                    addresses
                            .computeIfAbsent(address, ignored -> new HashSet<>())
                            .add(group.name);
                }
            }
        }

        LOGGER.info("Successfully refreshed allowed server list, retrieved {} addresses", addresses.size());
        this.addresses = addresses;
    }

    @Override
    public @Nullable Identity identify(final HttpExchange exchange) {
        final var group = this.addresses.get(exchange.getRemoteAddress().getAddress());
        if (group != null) {
            return new Identity("mindustry-server-list", group.stream().collect(Collectors.joining(",", "[", "]")));
        }
        return null;
    }

    private record MindustryServerGroup(
            String name,

            @SerializedName("address") @JsonAdapter(ServerListAdapter.class)
            Set<InetAddress> addresses) {}

    private static final class ServerListAdapter extends TypeAdapter<Set<InetAddress>> {

        @SuppressWarnings("EmptyCatch")
        @Override
        public @Nullable Set<InetAddress> read(final JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            final var addresses = new HashSet<InetAddress>();
            in.beginArray();
            while (in.hasNext()) {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    continue;
                }
                final var string = in.nextString();
                try {
                    addresses.add(InetAddress.getByName(extractHost(string)));
                } catch (final UnknownHostException _) {
                }
            }
            in.endArray();

            return addresses;
        }

        @Override
        public void write(final JsonWriter out, final @Nullable Set<InetAddress> value) {
            throw new UnsupportedOperationException();
        }

        private static String extractHost(final String value) {
            if (value.startsWith("[")) {
                final var end = value.indexOf(']');
                return end == -1 ? value : value.substring(1, end);
            }

            final var firstColon = value.indexOf(':');
            final var lastColon = value.lastIndexOf(':');
            if (firstColon != -1 && firstColon == lastColon) {
                return value.substring(0, lastColon);
            }

            return value;
        }
    }
}
