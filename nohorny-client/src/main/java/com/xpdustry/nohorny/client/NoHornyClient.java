// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import arc.Events;
import arc.util.serialization.Jval;
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

final class NoHornyClient implements LifecycleListener {

    private static final MiniLogger log = MiniLogger.forClass(NoHornyClient.class);
    private static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ZERO, Duration.ofSeconds(10), Duration.ofMinutes(2));

    private final HttpClient http = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("nohorny-client-worker-", 0).factory());

    private final Supplier<URI> endpoint = MindustryUtils.registerSafeSettingEntry(
            "nohorny-api-endpoint",
            "The NoHorny API endpoint to query for image rating.",
            URI.create("https://nohorny.xpdustry.com/api"),
            URI::create);

    @Override
    public void onInit() {
        final var request = HttpRequest.newBuilder(this.resolve("status"))
                .timeout(Duration.ofSeconds(5L))
                .GET()
                .build();
        final HttpResponse<String> response;
        try {
            response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (final ConnectException e) {
            log.error("The configured NoHorny server ({}) is not reachable, is it running?", this.resolve(""));
            return;
        } catch (final Exception e) {
            log.error("Failed to check the status of {}", this.resolve(""), e);
            return;
        }
        if (response.statusCode() != 200) {
            log.error("The NoHorny server returned {} on status check: {}", response.statusCode(), response.body());
        } else {
            log.info("The NoHorny server is operational: {}", response.body());
        }
    }

    public <T extends MindustryImage> void accept(final VirtualBuilding.Group<T> group) {
        this.executor.execute(() -> {
            for (int attempt = 0; attempt < RETRY_DELAYS.size(); attempt++) {
                try {
                    Thread.sleep(RETRY_DELAYS.get(attempt));
                    this.classify(group);
                    return;
                } catch (final InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final ConnectException e) {
                    log.error(
                            "Failed to rate group at ({}, {}), attempt {}/{}: The NoHorny server is not reachable",
                            group.x(),
                            group.y(),
                            attempt + 1,
                            RETRY_DELAYS.size());
                } catch (final Exception e) {
                    log.error(
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

    private <T extends MindustryImage> void classify(final VirtualBuilding.Group<T> group) throws Exception {
        final var request = HttpRequest.newBuilder(this.resolve("classify"))
                .timeout(Duration.ofSeconds(15L))
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
                            log.warn("Failed to stream request body for group at ({}, {})", group.x(), group.y(), e);
                        }
                    });
                    return in;
                }))
                .build();

        final var response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Remote detector returned " + response.statusCode() + ": " + response.body());
        }

        final var jval = Jval.read(response.body());
        final var classification = new ClassificationResponse(
                jval.getString("classifier"), Rating.valueOf(jval.getString("rating")), jval.getString("identifier"));

        final var author = computeAuthor(group);
        log.trace(
                "Received classification response for group by {} at ({}, {}): {} ({}/id={})",
                author == null ? "unknown" : author.uuid() + "/" + author.ip(),
                group.x(),
                group.y(),
                classification.rating(),
                classification.classifier(),
                classification.identifier());
        Core.app.post(() -> Events.fire(new ClassificationEvent(
                group, classification.rating(), computeAuthor(group), classification.identifier())));
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
