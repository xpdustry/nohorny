// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.util.serialization.Jval;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

final class ProxiflyProxySelector extends ProxySelector {

    private static final MiniLogger log = MiniLogger.forClass(ProxiflyProxySelector.class);
    private static final URI PROXY_LIST_ENDPOINT =
            URI.create("https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/countries/");
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final int TEST_BATCH_SIZE = 16;

    private final Executor executor;
    private final Object lock = new Object();
    private @Nullable Proxy proxy = null;
    private Instant refreshAt = Instant.MIN;

    ProxiflyProxySelector(final Executor executor) {
        this.executor = executor;
    }

    @Override
    public List<Proxy> select(final URI uri) {
        Objects.requireNonNull(uri.getScheme(), "uri.scheme");
        Objects.requireNonNull(uri.getHost(), "uri.host");

        if (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https")) {
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
            final var endpoint = NoHornySetting.API_ENDPOINT.get();
            if (endpoint == null) {
                this.refreshAt = Instant.MIN;
                return null;
            }

            final var countries = Objects.requireNonNullElse(NoHornySetting.PROXY_COUNTRIES.get(), "US");
            final var proxies = this.fetchProxies(Arrays.stream(countries.split(",", -1))
                    .map(c -> c.toUpperCase(Locale.ROOT))
                    .toList());
            final var status = HttpUtils.appendPathSegments(endpoint, "status");

            Proxy result = null;
            for (int start = 0; start < proxies.size(); start += TEST_BATCH_SIZE) {
                final var batch = proxies.subList(start, Math.min(start + TEST_BATCH_SIZE, proxies.size()));
                final var proxy = this.testProxyBatch(batch, status);
                if (proxy != null) {
                    log.debug("Selected the Proxifly proxy {}", proxy.address());
                    result = proxy;
                    break;
                }
            }
            if (result != null) {
                this.proxy = result;
            } else {
                log.error("No working Proxifly proxy was found for the countries {}", countries);
            }

            this.refreshAt = Instant.now().plus(10, ChronoUnit.MINUTES);
            return this.proxy;
        }
    }

    private @Nullable Proxy testProxyBatch(final List<InetSocketAddress> batch, final URI status) {
        final var completion = new ExecutorCompletionService<Optional<Proxy>>(this.executor);
        final var tasks = new ArrayList<Future<Optional<Proxy>>>(batch.size());
        try {
            for (final var address : batch) {
                tasks.add(completion.submit(() -> this.testProxy(address, status)));
            }
            final var deadline = System.nanoTime() + TIMEOUT.toNanos();
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
    private Optional<Proxy> testProxy(final InetSocketAddress address, final URI status) {
        try (final var http = HttpClient.newBuilder()
                .proxy(ProxySelector.of(address))
                .connectTimeout(TIMEOUT)
                .build()) {
            final var response = http.send(
                    HttpRequest.newBuilder(status).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                return Optional.of(new Proxy(Proxy.Type.HTTP, address));
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final IOException _) {
        }
        return Optional.empty();
    }

    // TODO Parallelize too?
    @SuppressWarnings("EmptyCatch")
    private List<InetSocketAddress> fetchProxies(final List<String> countries) {
        final var addresses = new HashSet<InetSocketAddress>();
        try (final var http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            for (final var country : countries) {
                final var request = HttpRequest.newBuilder(
                                HttpUtils.appendPathSegments(PROXY_LIST_ENDPOINT, country, "data.json"))
                        .timeout(TIMEOUT)
                        .GET()
                        .build();
                final HttpResponse<String> response;
                try {
                    response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    continue;
                } catch (final IOException e) {
                    log.error("Failed to fetch the Proxifly proxy list of {}", country, e);
                    continue;
                }
                if (response.statusCode() != 200) {
                    log.error(
                            "The Proxifly proxy list of {} returned the http code {}", country, response.statusCode());
                    continue;
                }
                for (final var element : Jval.read(response.body()).asArray()) {
                    try {
                        final var address = this.parseProxy(element);
                        if (address != null) {
                            addresses.add(address);
                        }
                    } catch (final Exception _) {
                    }
                }
            }
            return List.copyOf(addresses);
        }
    }

    private @Nullable InetSocketAddress parseProxy(final Jval element) {
        final var protocol = element.getString("protocol", "");
        if (!protocol.equals("http") && !protocol.equals("https")) {
            return null;
        }
        final var ip = element.getString("ip", "");
        if (ip.isEmpty()) {
            return null;
        }
        final var port = element.getInt("port", -1);
        if (port <= 0 || port > 65535) {
            return null;
        }
        return new InetSocketAddress(InetAddress.ofLiteral(ip), port);
    }
}
