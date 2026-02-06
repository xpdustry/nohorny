// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny;

import arc.math.geom.Point2;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xpdustry.nohorny.authentication.Authenticator;
import com.xpdustry.nohorny.classification.Classifier;
import com.xpdustry.nohorny.config.NoHornyConfig;
import com.xpdustry.nohorny.geometry.VirtualBuilding;
import com.xpdustry.nohorny.http.DetectionBatchResponse;
import com.xpdustry.nohorny.http.DetectionRequester;
import com.xpdustry.nohorny.http.DetectionResult;
import com.xpdustry.nohorny.http.HealthResponse;
import com.xpdustry.nohorny.image.ImageBinaryCodec;
import com.xpdustry.nohorny.image.ImageRenderer;
import com.xpdustry.nohorny.image.MindustryImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoHornyServer implements NoHornyListener {

    private static final Logger logger = LoggerFactory.getLogger(NoHornyServer.class);

    private final NoHornyConfig.ServerConfig config;
    private final ScheduledExecutorService executor;
    private final Gson gson;
    private final List<Classifier> classifiers;
    private final List<Authenticator> authenticators;
    private final ImageRenderer renderer;
    private final ImageBinaryCodec codec;
    private @Nullable HttpServer server;

    public NoHornyServer(
            final NoHornyConfig.ServerConfig config,
            final ScheduledExecutorService executor,
            final Gson gson,
            final List<Classifier> classifiers,
            final List<Authenticator> authenticators,
            final ImageRenderer renderer,
            final ImageBinaryCodec codec) {
        this.config = config;
        this.executor = executor;
        this.gson = gson;
        this.classifiers = List.copyOf(classifiers);
        this.authenticators = List.copyOf(authenticators);
        this.renderer = renderer;
        this.codec = codec;
        if (this.classifiers.isEmpty()) {
            throw new IllegalArgumentException("At least one classifier is required");
        }
        if (this.authenticators.isEmpty()) {
            throw new IllegalArgumentException("At least one authenticator is required");
        }
    }

    @Override
    public void onInit() {
        try {
            final var server = HttpServer.create(new InetSocketAddress(this.config.host(), this.config.port()), 0);
            server.setExecutor(this.executor);
            server.createContext("/v4/classify", new ClassifyHandler());
            server.createContext("/v4/health", exchange -> writeJson(exchange, 200, new HealthResponse("ok")));
            this.server = server;
            server.start();
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to start detection HTTP server", e);
        }
    }

    @Override
    public void onExit() {
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }
    }

    private void writeJson(final HttpExchange exchange, final int status, final Object payload) throws IOException {
        final var json = this.gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, json.length);
        try (final var output = exchange.getResponseBody()) {
            output.write(json);
        }
    }

    private static void writeText(final HttpExchange exchange, final int status, final String text) throws IOException {
        final var body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (final var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private final class ClassifyHandler implements HttpHandler {

        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    writeText(exchange, 405, "method not allowed");
                    return;
                }

                Authenticator.Identity identity = null;
                for (final var authenticator : authenticators) {
                    identity = authenticator.identify(exchange);
                    if (identity != null) {
                        break;
                    }
                }

                if (identity == null) {
                    writeText(exchange, 401, "unauthorized");
                    return;
                }

                final var requester = new DetectionRequester(identity.type(), identity.name());
                final var groups = codec.decode(exchange.getRequestBody());
                final var detections = groups.stream()
                        .map(group -> Map.entry(
                                Point2.pack(group.x(), group.y()),
                                this.detect(group, requester).getFirst()))
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
                writeJson(exchange, 200, new DetectionBatchResponse(detections));
            } catch (final Exception e) {
                logger.error("Detection request failed", e);
                writeText(exchange, 500, "internal server error");
            }
        }

        public List<DetectionResult> detect(
                final VirtualBuilding.Group<? extends MindustryImage> group, final DetectionRequester requester) {
            final var rendered = renderer.render(group);
            final var results = new ArrayList<DetectionResult>(classifiers.size());

            for (final var classifier : classifiers) {
                try {
                    final var result = classifier.classify(rendered);
                    final var detection =
                            new DetectionResult(classifier.name(), classifier.version(), result.classification());
                    results.add(detection);
                    logger.trace(
                            "Detection result for {}:{} via {} {} => {} {}",
                            requester.type(),
                            requester.name(),
                            detection.classifier(),
                            detection.version(),
                            detection.classification(),
                            result.json());
                } catch (final Exception e) {
                    logger.error(
                            "Classifier {} failed for {}:{}", classifier.name(), requester.type(), requester.name(), e);
                }
            }

            if (results.isEmpty()) {
                throw new IllegalStateException(
                        "No detection result produced for " + requester.type() + ":" + requester.name());
            }
            return List.copyOf(results);
        }
    }
}
