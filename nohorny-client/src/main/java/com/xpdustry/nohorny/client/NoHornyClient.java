// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import arc.Events;
import arc.util.serialization.Jval;
import com.github.mizosoft.methanol.Methanol;
import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.MindustryImageIO;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.jspecify.annotations.Nullable;

final class NoHornyClient implements LifecycleListener {

    private static final MiniLogger log = MiniLogger.forClass(NoHornyClient.class);

    private final Methanol http;
    private final Semaphore semaphore = new Semaphore(1);
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("nohorny-client-worker-", 0).factory());

    NoHornyClient(final Methanol http) {
        this.http = http;
        MindustryUtils.onEvent(SettingChangeEvent.class, event -> {
            if (event.key().equals(NoHornySetting.API_ENDPOINT)
                    || event.key().equals(NoHornySetting.API_AUTH_TYPE)
                    || event.key().equals(NoHornySetting.API_AUTH_VALUE)) {
                this.checkEndpointStatus();
            }
        });
    }

    @Override
    public void onInit() {
        this.checkEndpointStatus();
    }

    private void checkEndpointStatus() {
        final var endpoint = NoHornySetting.API_ENDPOINT.get();
        if (endpoint == null) {
            return;
        }
        final HttpResponse<String> response;
        try {
            final var request =
                    this.request("status", Duration.ofSeconds(5L)).GET().build();
            response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (final ConnectException e) {
            log.error("The NoHorny server {} is not reachable, is it running?", endpoint);
            return;
        } catch (final Exception e) {
            log.error("Failed to check the status of {}", endpoint, e);
            return;
        }
        final var message = getGenericServerMessage(response);
        if (response.statusCode() != 200) {
            log.error(
                    "The NoHorny server {} returned {} on status check: {}", endpoint, response.statusCode(), message);
        } else {
            log.info("The NoHorny server {} is operational: {}", endpoint, message);
        }
    }

    // This is not a perfect concurrency guard, but it's simple, and it's also ok if a few groups slip through
    public boolean canAccept() {
        return this.semaphore.availablePermits() != 0;
    }

    public <T extends MindustryImage> void accept(final VirtualBuilding.Group<T> group) {
        this.executor.execute(() -> {
            try {
                this.semaphore.acquire();
                try {
                    this.classify(group);
                } catch (final ConnectException e) {
                    log.error(
                            "Failed to rate group at ({}, {}): The NoHorny server is not reachable",
                            group.x(),
                            group.y());
                } catch (final Exception e) {
                    log.error("Failed to rate group at ({}, {})", group.x(), group.y(), e);
                } finally {
                    this.semaphore.release();
                }
            } catch (final InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private <T extends MindustryImage> void classify(final VirtualBuilding.Group<T> group) throws Exception {
        final var request = this.request("classify", Duration.ofSeconds(15))
                .header("Content-Type", MindustryImageIO.MEDIA_TYPE)
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> {
                    final var in = new PipedInputStream(4 * 1024);
                    final PipedOutputStream out;
                    try {
                        out = new PipedOutputStream(in);
                    } catch (final IOException e) {
                        throw new IllegalStateException("Failed to connect request body pipe", e);
                    }
                    this.executor.execute(() -> {
                        try (out) {
                            MindustryImageIO.writeImageGroup(out, group);
                        } catch (final IOException e) {
                            log.error("Failed to stream request body for group at ({}, {})", group.x(), group.y(), e);
                        }
                    });
                    return in;
                }))
                .build();

        final var response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            final var message = getGenericServerMessage(response);
            log.error("The remote nohorny returned http code {}: {}", response.statusCode(), message);
            return;
        }

        final ClassificationResponse classification;
        try {
            final var jval = Jval.read(response.body());
            classification = new ClassificationResponse(
                    jval.getString("classifier"),
                    Rating.valueOf(jval.getString("rating")),
                    jval.getFloat("confidence", 0F),
                    jval.getString("identifier"));
        } catch (final Exception e) {
            log.error("The remote nohorny server returned a malformed response: {}", response.body(), e);
            return;
        }

        final var author = computeAuthor(group);
        log.debug(
                "Received classification response for group at ({}, {}) by {}: {} rating at {}% confidence from {} (trace-id={})",
                author == null ? "unknown" : author.uuid() + "/" + author.ip(),
                group.x(),
                group.y(),
                classification.rating(),
                "%.2f".formatted(classification.confidence() * 100),
                classification.classifier(),
                classification.identifier());
        Core.app.post(() -> Events.fire(new ClassificationEvent(group, computeAuthor(group), classification)));
    }

    private HttpRequest.Builder request(final String path, final Duration timeout) {
        final var endpoint = NoHornySetting.API_ENDPOINT.get();
        if (endpoint == null) {
            throw new IllegalStateException("NoHorny API endpoint is disabled");
        }
        final var base = endpoint.toString();
        final var normalized = base.endsWith("/") ? base : base + "/";
        final var uri = URI.create(normalized).resolve(path);
        final var request = HttpRequest.newBuilder(uri).timeout(timeout);
        final var authorization = this.authorization();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return request;
    }

    private @Nullable String authorization() {
        final var type = NoHornySetting.API_AUTH_TYPE.get();
        if (type == null) {
            return null;
        }
        final var value = NoHornySetting.API_AUTH_VALUE.get();
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (type) {
            case DISABLED -> null;
            case BASIC -> "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            case BEARER -> "Bearer " + value;
        };
    }

    @SuppressWarnings("NullAway")
    private static @Nullable MindustryAuthor computeAuthor(
            final VirtualBuilding.Group<? extends MindustryImage> group) {
        final var authors = new ArrayList<MindustryAuthor>();
        var total = 0;
        for (final var element : group.elements()) {
            switch (element.data()) {
                case MindustryCanvas canvas -> {
                    total++;
                    if (canvas.author() != null) {
                        authors.add(canvas.author());
                    }
                }
                case MindustryDisplay display -> {
                    for (final var processor : display.processors().values()) {
                        total++;
                        if (processor.author() != null) {
                            authors.add(processor.author());
                        }
                    }
                }
            }
        }

        if (authors.isEmpty() || (float) authors.size() / total < 0.4F) {
            return null;
        }

        final var counts = new HashMap<String, Integer>();
        MindustryAuthor best = null;
        for (final var author : authors) {
            final var address = author.ip();
            counts.compute(address, (_, v) -> v == null ? 1 : v + 1);
            if (best == null || counts.get(address) > counts.get(best.ip())) {
                best = author;
            }
        }

        return best;
    }

    private static String getGenericServerMessage(final HttpResponse<String> request) {
        final var value = request.body();
        try {
            return Jval.read(request.body()).getString("message", value);
        } catch (final Exception _) {
            return value;
        }
    }
}
