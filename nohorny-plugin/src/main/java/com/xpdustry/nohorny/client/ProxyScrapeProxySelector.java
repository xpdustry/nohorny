// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.util.serialization.Jval;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import org.jspecify.annotations.Nullable;

final class ProxyScrapeProxySelector extends ProxySelector implements AutoCloseable {

    private static final MiniLogger log = MiniLogger.forClass(ProxyScrapeProxySelector.class);

    private static final URI PROXY_LIST_ENDPOINT = URI.create(
            "https://api.proxyscrape.com/v4/free-proxy-list/get?request=display_proxies&protocol=http&ssl=yes&proxy_format=ipport&format=text");
    private static final URL DISCORD_STATUS_ENDPOINT;

    static {
        try {
            DISCORD_STATUS_ENDPOINT =
                    URI.create("https://discord.com/api/v10/gateway").toURL();
        } catch (final MalformedURLException e) {
            throw new RuntimeException("How?", e);
        }
    }

    private static final Duration PROXY_LIST_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration PROXY_CONNECT_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration PROXY_READ_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration PROXY_BATCH_TIMEOUT =
            PROXY_CONNECT_TIMEOUT.plus(PROXY_READ_TIMEOUT).plusSeconds(1L);
    private static final int TEST_BATCH_SIZE = 32;
    private static final int PROXY_LIST_SIZE_LIMIT = 256;

    private final Executor executor;
    private final HttpClient http;
    private final Object lock = new Object();
    private @Nullable Proxy proxy = null;
    private Instant refreshAt = Instant.MIN;

    ProxyScrapeProxySelector(final Executor executor) {
        this.executor = executor;
        this.http = HttpClient.newBuilder()
                .connectTimeout(PROXY_LIST_TIMEOUT)
                .executor(this.executor)
                .build();
    }

    @Override
    public List<Proxy> select(final URI uri) {
        Objects.requireNonNull(uri.getScheme(), "uri.scheme");
        Objects.requireNonNull(uri.getHost(), "uri.host");

        if (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https")) {
            return ProxySelector.getDefault().select(uri);
        }

        if (!Boolean.TRUE.equals(NoHornySetting.DISCORD_WEBHOOK_PROXY.get())) {
            return ProxySelector.getDefault().select(uri);
        }

        final var proxy = this.refresh(false);
        return proxy == null ? ProxySelector.getDefault().select(uri) : List.of(proxy);
    }

    @Override
    public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
        Objects.requireNonNull(uri.getScheme(), "uri.scheme");
        Objects.requireNonNull(uri.getHost(), "uri.host");

        if (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https")) {
            return;
        }

        synchronized (this.lock) {
            if (this.proxy != null && this.proxy.address().equals(sa)) {
                this.proxy = null;
                this.refreshAt = Instant.MIN;
            }
        }
    }

    @Nullable Proxy refresh(final boolean force) {
        synchronized (this.lock) {
            if (!force && this.refreshAt.isAfter(Instant.now())) {
                return this.proxy;
            }

            final var start = System.currentTimeMillis();
            final var proxies = this.fetchProxies();

            Proxy result = null;
            for (int i = 0; i < proxies.size(); i += TEST_BATCH_SIZE) {
                final var batch = proxies.subList(i, Math.min(i + TEST_BATCH_SIZE, proxies.size()));
                final var proxy = this.testProxyBatch(batch);
                if (proxy != null) {
                    log.log(
                            force ? MiniLogger.Level.INFO : MiniLogger.Level.DEBUG,
                            "Selected the ProxyScrape proxy {} in {}ms",
                            proxy.address(),
                            System.currentTimeMillis() - start);
                    result = proxy;
                    break;
                }
            }
            if (result != null) {
                this.proxy = result;
                this.refreshAt = Instant.now().plus(10, ChronoUnit.MINUTES);
            } else {
                log.error("No working ProxyScrape proxy was found");
                this.proxy = null;
                this.refreshAt = Instant.MIN;
            }

            return this.proxy;
        }
    }

    private @Nullable Proxy testProxyBatch(final List<InetSocketAddress> batch) {
        final var completion = new ExecutorCompletionService<Optional<Proxy>>(this.executor);
        final var tasks = new ArrayList<Future<Optional<Proxy>>>(batch.size());
        try {
            for (final var address : batch) {
                tasks.add(completion.submit(() -> this.testProxy(address)));
            }
            final var deadline = System.nanoTime() + PROXY_BATCH_TIMEOUT.toNanos();
            for (int i = 0; i < tasks.size(); i++) {
                final var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                final Future<Optional<Proxy>> completed;
                try {
                    completed = completion.poll(remaining, TimeUnit.NANOSECONDS);
                } catch (final InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                if (completed == null) {
                    break;
                }
                if (completed.state() == Future.State.SUCCESS) {
                    final var proxy = completed.resultNow();
                    if (proxy.isPresent()) {
                        for (final var task : tasks) {
                            task.cancel(true);
                        }
                        return proxy.get();
                    }
                }
            }
        } finally {
            for (final var task : tasks) {
                task.cancel(true);
            }
        }
        return null;
    }

    @SuppressWarnings("EmptyCatch")
    private Optional<Proxy> testProxy(final InetSocketAddress address) {
        final var proxy = new Proxy(Proxy.Type.HTTP, address);
        final HttpsURLConnection connection;
        try {
            connection = (HttpsURLConnection) DISCORD_STATUS_ENDPOINT.openConnection(proxy);
        } catch (final IOException e) {
            return Optional.empty();
        }
        try {
            connection.setConnectTimeout((int) PROXY_CONNECT_TIMEOUT.toMillis());
            connection.setReadTimeout((int) PROXY_READ_TIMEOUT.toMillis());
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setRequestProperty("Connection", "close");
            if (connection.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                try (final var stream = connection.getInputStream()) {
                    final var body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    if (parseGatewayUri(body) != null) {
                        return Optional.of(proxy);
                    }
                }
            }
        } catch (final IOException _) {
        } finally {
            try {
                final var error = connection.getErrorStream();
                if (error != null) {
                    error.close();
                }
            } catch (final IOException _) {
            }
            connection.disconnect();
        }
        return Optional.empty();
    }

    // Some proxies return fake responses, mostly as fake HTML pages, run some basic checks to filter them out
    private static @Nullable URI parseGatewayUri(final String response) {
        try {
            final var url = Jval.read(response).getString("url", null);
            if (url == null) {
                return null;
            }
            final var gateway = URI.create(url);
            return gateway.isAbsolute() && gateway.getHost() != null && "wss".equalsIgnoreCase(gateway.getScheme())
                    ? gateway
                    : null;
        } catch (final Exception _) {
            return null;
        }
    }

    @SuppressWarnings("EmptyCatch")
    private List<InetSocketAddress> fetchProxies() {
        final var request = HttpRequest.newBuilder(PROXY_LIST_ENDPOINT)
                .timeout(PROXY_LIST_TIMEOUT)
                .GET()
                .build();
        final HttpResponse<String> response;
        try {
            response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (final IOException e) {
            log.error("Failed to fetch the ProxyScrape proxy list", e);
            return List.of();
        }
        if (response.statusCode() != 200) {
            log.error("The ProxyScrape proxy list returned http code {}", response.statusCode());
            return List.of();
        }

        return response.body()
                .lines()
                .map(line -> {
                    final var split = line.lastIndexOf(':');
                    if (split == -1) {
                        return null;
                    }

                    final InetAddress address;
                    try {
                        address = InetAddress.ofLiteral(line.substring(0, split));
                    } catch (final IllegalArgumentException _) {
                        return null;
                    }

                    final int port;
                    try {
                        port = Integer.parseInt(line.substring(split + 1));
                    } catch (final NumberFormatException _) {
                        return null;
                    }
                    if (port <= 0 || port > 65535) {
                        return null;
                    }

                    return new InetSocketAddress(address, port);
                })
                .filter(Objects::nonNull)
                .limit(PROXY_LIST_SIZE_LIMIT)
                .toList();
    }

    @Override
    public void close() {
        this.http.close();
    }
}
