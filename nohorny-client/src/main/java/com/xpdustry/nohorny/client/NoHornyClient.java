// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import arc.util.serialization.Jval;
import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.MindustryPixel;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import org.jspecify.annotations.Nullable;

final class NoHornyClient implements LifecycleListener, GroupClassifier {

    private static final MiniLogger log = MiniLogger.forClass(NoHornyClient.class);
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int MAX_RATE_LIMITED_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(10);
    private static final Duration MAX_RETRY_AFTER = Duration.ofMinutes(2);

    private final ReusableImageBytes imageBuffer = new ReusableImageBytes();
    private final Semaphore classificationPermits = new Semaphore(1);
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("nohorny-client-worker-", 0).factory());
    private final HttpClient http =
            HttpClient.newBuilder().executor(this.executor).build();
    private final NoHornyEventBus events;

    public NoHornyClient(final NoHornyEventBus events) {
        this.events = events;
    }

    @Override
    public void onInit() {
        this.imageBuffer.onInit();
        this.events.subscribe(SettingChangeEvent.class, event -> {
            if (event.key().equals(NoHornySetting.API_ENDPOINT)
                    || event.key().equals(NoHornySetting.API_AUTH_TYPE)
                    || event.key().equals(NoHornySetting.API_AUTH_VALUE)) {
                this.checkEndpointStatus();
            }
        });

        this.checkEndpointStatus();
    }

    @Override
    public void onExit() {
        this.executor.close();
        this.http.close();
        this.imageBuffer.onExit();
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

    @Override
    public boolean tryAccept(final VirtualBuilding.Group<? extends MindustryImage> group) {
        if (!this.classificationPermits.tryAcquire()) {
            return false;
        }
        try {
            this.executor.execute(() -> {
                this.imageBuffer.lock();
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
                    this.imageBuffer.release();
                    this.classificationPermits.release();
                }
            });
            return true;
        } catch (final RejectedExecutionException _) {
            this.classificationPermits.release();
            return false;
        }
    }

    private void classify(final VirtualBuilding.Group<? extends MindustryImage> group) throws Exception {
        final var request = this.request("classify", Duration.ofSeconds(15))
                .header("Content-Type", "image/jpeg")
                .POST(this.imageBuffer.encodeJpeg(MindustryImageRenderer.render(group)))
                .build();

        final var response = this.send(request);
        if (response.statusCode() != 200) {
            final var message = getGenericServerMessage(response);
            log.error("The remote nohorny returned http code {}: {}", response.statusCode(), message);
            return;
        }

        final ClassificationResponse classification;
        try {
            final var jval = Jval.read(response.body());
            final var url = jval.get("url");
            classification = new ClassificationResponse(
                    jval.getString("classifier"),
                    Rating.valueOf(jval.getString("rating")),
                    jval.getFloat("confidence", 0F),
                    jval.getString("identifier"),
                    url != null && url.isString() && !url.asString().isBlank() ? url.asString() : null);
        } catch (final Exception e) {
            log.error("The remote nohorny server returned a malformed response: {}", response.body(), e);
            return;
        }

        final var author = computeAuthor(group);
        log.log(
                classification.rating().isWorseOrEqualThan(Rating.WARN)
                        ? MiniLogger.Level.INFO
                        : MiniLogger.Level.DEBUG,
                "Received classification response for group at ({}, {}) by {}: {} rating at {}% confidence from {} (trace-id={}{})",
                group.x(),
                group.y(),
                author == null ? "unknown" : author.uuid() + "/" + author.ip(),
                classification.rating(),
                "%.2f".formatted(classification.confidence() * 100),
                classification.classifier(),
                classification.identifier(),
                classification.url() == null ? "" : ", url=" + classification.url());
        Core.app.post(() -> this.events.publish(new ClassificationEvent(group, author, classification)));
    }

    /// Sends the request, waiting out the rate limit of the server between the attempts.
    ///
    /// The worker keeps its classification permit while it waits,
    /// so the collectors hold their next group meanwhile.
    private HttpResponse<String> send(final HttpRequest request) throws Exception {
        var attempt = 1;
        while (true) {
            final var response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != TOO_MANY_REQUESTS || attempt == MAX_RATE_LIMITED_ATTEMPTS) {
                return response;
            }
            final var wait = retryAfter(response);
            log.info(
                    "The NoHorny server is rate limiting this server, retrying in {}s (attempt {}/{})",
                    wait.toSeconds(),
                    attempt,
                    MAX_RATE_LIMITED_ATTEMPTS);
            Thread.sleep(wait);
            attempt++;
        }
    }

    /// @return the wait announced by the `Retry-After` header, in seconds or as an HTTP date, capped
    private static Duration retryAfter(final HttpResponse<?> response) {
        final var header = response.headers().firstValue("Retry-After").orElse(null);
        Duration wait = DEFAULT_RETRY_AFTER;
        if (header != null) {
            try {
                wait = Duration.ofSeconds(Long.parseLong(header.trim()));
            } catch (final NumberFormatException _) {
                try {
                    wait = Duration.between(
                            Instant.now(), DateTimeFormatter.RFC_1123_DATE_TIME.parse(header.trim(), Instant::from));
                } catch (final DateTimeParseException _) {
                    log.debug("Ignoring the malformed Retry-After header {}", header);
                }
            }
        }
        if (wait.isNegative() || wait.isZero()) {
            return Duration.ofSeconds(1);
        }
        return wait.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : wait;
    }

    private HttpRequest.Builder request(final String path, final Duration timeout) {
        final var endpoint = NoHornySetting.API_ENDPOINT.get();
        if (endpoint == null) {
            throw new IllegalStateException("NoHorny API endpoint is disabled");
        }
        final var request = HttpRequest.newBuilder(HttpUtils.appendPathSegments(endpoint, path))
                .timeout(timeout)
                .header("User-Agent", NoHornyPlugin.USER_AGENT)
                .header("X-NoHorny-Version", NoHornyPlugin.VERSION);
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
                case MindustryPixel pixel -> {
                    total++;
                    if (pixel.author() != null) {
                        authors.add(pixel.author());
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
