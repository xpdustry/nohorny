// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import arc.Events;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.xpdustry.nohorny.common.classification.ClassificationResponse;
import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.image.ImageBinaryCodec;
import com.xpdustry.nohorny.common.image.MindustryAuthor;
import com.xpdustry.nohorny.common.image.MindustryCanvas;
import com.xpdustry.nohorny.common.image.MindustryDisplay;
import com.xpdustry.nohorny.common.image.MindustryImage;
import com.xpdustry.nohorny.common.lifecycle.LifecycleListener;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoHornyClient implements LifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoHornyClient.class);
    private static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ZERO, Duration.ofSeconds(10), Duration.ofMinutes(2));

    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final HttpClient http = HttpClient.newHttpClient();
    // TODO Named threads?
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final Supplier<URI> endpoint = ConfigUtils.registerSafeSettingEntry(
            "nohorny-api-endpoint",
            "The NoHorny API endpoint to query for image rating.",
            URI.create("https://nohorny.xpdustry.com/api"),
            URI::create);

    @Override
    public void onInit() {
        final var request = HttpRequest.newBuilder(this.resolve("v4/status"))
                .timeout(Duration.ofSeconds(5L))
                .GET()
                .build();
        final HttpResponse<String> response;
        try {
            response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (final ConnectException e) {
            LOGGER.error("The configured NoHorny server ({}) is not reachable, is it running?", this.resolve(""));
            return;
        } catch (final Exception e) {
            LOGGER.error("Failed to check the status of {}", this.resolve(""), e);
            return;
        }
        if (response.statusCode() != 200) {
            LOGGER.error("The NoHorny server returned {} on status check: {}", response.statusCode(), response.body());
        } else {
            LOGGER.info("The NoHorny server is operational: {}", response.body());
        }
    }

    public <T extends MindustryImage> void accept(final VirtualBuilding.Group<T> group) {
        this.executor.execute(() -> {
            for (int attempt = 0; attempt < RETRY_DELAYS.size(); attempt++) {
                try {
                    Thread.sleep(RETRY_DELAYS.get(attempt));
                    this.rate(group);
                    return;
                } catch (final InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final ConnectException e) {
                    LOGGER.error(
                            "Failed to rate group at ({}, {}), attempt {}/{}: The NoHorny server is not reachable",
                            group.x(),
                            group.y(),
                            attempt + 1,
                            RETRY_DELAYS.size());
                } catch (final Exception e) {
                    LOGGER.error(
                            "Failed to rate group at ({}, {}), attempt {}/{}",
                            group.x(),
                            group.y(),
                            attempt + 1,
                            RETRY_DELAYS.size(),
                            e);
                }
            }
        });
    }

    private <T extends MindustryImage> void rate(final VirtualBuilding.Group<T> group) throws Exception {
        final var request = HttpRequest.newBuilder(this.resolve("v4/rate"))
                .timeout(Duration.ofSeconds(15L))
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> {
                    final var in = new PipedInputStream();
                    final PipedOutputStream out;
                    try {
                        out = new PipedOutputStream(in);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    this.executor.execute(() -> {
                        try {
                            ImageBinaryCodec.encode(out, group);
                        } catch (final IOException e1) {
                            try {
                                out.close();
                            } catch (final IOException e2) {
                                e1.addSuppressed(e2);
                            }
                            throw new UncheckedIOException(e1);
                        }
                    });
                    return in;
                }))
                .build();

        final var response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            LOGGER.error(
                    "Received error response (code={}) for group at ({}, {}): {}",
                    response.statusCode(),
                    group.x(),
                    group.y(),
                    response.body());
            throw new IllegalStateException(
                    "Remote detector returned " + response.statusCode() + ": " + response.body());
        }

        final var classification = this.gson.fromJson(response.body(), ClassificationResponse.class);
        LOGGER.trace(
                "Received classification response for group at ({}, {}): {} ({})",
                group.x(),
                group.y(),
                classification.rating(),
                classification.classifier());
        Core.app.post(() -> Events.fire(new ClassificationEvent(group, classification.rating(), computeAuthor(group))));
    }

    private URI resolve(final String path) {
        final var base = this.endpoint.get().toString();
        final var normalizedBase = base.endsWith("/") ? base : base + "/";
        return URI.create(normalizedBase).resolve(path);
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
}
