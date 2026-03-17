// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xpdustry.nohorny.common.classification.ClassificationResponse;
import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.image.ImageBinaryCodec;
import com.xpdustry.nohorny.common.image.ImageRenderer;
import com.xpdustry.nohorny.common.image.MindustryImage;
import com.xpdustry.nohorny.common.lifecycle.LifecycleListener;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoHornyServer implements LifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoHornyServer.class);

    private final Gson gson = new Gson();
    private final String host;
    private final int port;
    private final Classifier classifier;
    private final KnowYourClient knows;
    private @Nullable ExecutorService executor;
    private @Nullable HttpServer server;

    public NoHornyServer(final String host, final int port, final Classifier classifier, final KnowYourClient knows) {
        this.host = host;
        this.port = port;
        this.classifier = classifier;
        this.knows = knows;
    }

    @Override
    public void onInit() {
        try {
            this.executor = Executors.newVirtualThreadPerTaskExecutor();
            final var server = HttpServer.create(new InetSocketAddress(this.host, this.port), 0);
            server.setExecutor(this.executor);
            server.createContext("/v4/classify", new ClassifyHandler());
            server.createContext("/v4/status", exchange -> writeText(exchange, 200, "ok"));
            this.server = server;
            server.start();
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to start NoHorny HTTP server", e);
        }
    }

    @Override
    public void onExit() {
        if (this.server != null) {
            this.server.stop(0);
        }
        if (this.executor != null) {
            this.executor.shutdown();
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
                final var info = knows.whois(exchange);
                writeJson(exchange, 200, this.classify(ImageBinaryCodec.decode(exchange.getRequestBody()), info));
            } catch (final Exception e) {
                LOGGER.error("Rating request failed", e);
                writeText(exchange, 500, "internal server error");
            }
        }

        private ClassificationResponse classify(
                final VirtualBuilding.Group<? extends MindustryImage> group, final KnowYourClient.Info info) {
            try {
                final var result = classifier.classify(ImageRenderer.render(group));
                final var classification = new ClassificationResponse(classifier.name(), result.rating());
                LOGGER.trace(
                        "Detection result for {}:{} via {} => {} {}",
                        info.type(),
                        info.name(),
                        classification.classifier(),
                        classification.rating(),
                        result.json());
                return classification;
            } catch (final Exception e) {
                LOGGER.error("Classifier {} failed for {}:{}", classifier.name(), info.type(), info.name(), e);
                throw new IllegalStateException("no classifier produced a result");
            }
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

    private record Requester(String type, String name) {}
}
