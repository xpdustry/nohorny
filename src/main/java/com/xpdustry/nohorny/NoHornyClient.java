// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny;

import arc.Core;
import arc.Events;
import arc.math.geom.Point2;
import com.github.mizosoft.methanol.MediaType;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MoreBodyPublishers;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.Gson;
import com.xpdustry.nohorny.config.NoHornyConfig;
import com.xpdustry.nohorny.geometry.ImmutablePoint2;
import com.xpdustry.nohorny.geometry.VirtualBuilding;
import com.xpdustry.nohorny.http.DetectionBatchResponse;
import com.xpdustry.nohorny.http.HealthResponse;
import com.xpdustry.nohorny.image.ImageBinaryCodec;
import com.xpdustry.nohorny.image.MindustryImage;
import com.xpdustry.nohorny.tracking.ClassificationEvent;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoHornyClient implements NoHornyListener {

    private static final Logger logger = LoggerFactory.getLogger(NoHornyClient.class);

    private final Executor executor;
    private final Methanol http;
    private final Gson gson;
    private final NoHornyConfig.ClientConfig config;
    private final ImageBinaryCodec codec;

    public NoHornyClient(
            final Executor executor,
            final Methanol http,
            final Gson gson,
            final NoHornyConfig.ClientConfig config,
            final ImageBinaryCodec codec) {
        this.executor = executor;
        this.http = http;
        this.gson = gson;
        this.codec = codec;
        this.config = config;
    }

    @Override
    public void onInit() {
        try {
            final var response = this.http.send(
                    MutableRequest.GET(resolve(this.config.endpoint(), "v4/health"))
                            .timeout(this.config.timeout()),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Remote detector healthcheck returned " + response.statusCode() + ": " + response.body());
            }
            final var health = Objects.requireNonNull(
                    this.gson.fromJson(response.body(), HealthResponse.class), "Missing healthcheck response");
            if (!"ok".equalsIgnoreCase(health.status())) {
                throw new IllegalStateException(
                        "Remote detector healthcheck returned unexpected status: " + health.status());
            }
            logger.info("Remote detector healthcheck passed for {}", this.config.endpoint());
        } catch (final Exception e) {
            throw new RuntimeException("The nohorny server at " + this.config.endpoint() + " is not healthy", e);
        }
    }

    public void accept(final List<? extends VirtualBuilding.Group<? extends MindustryImage>> groups) {
        if (groups.isEmpty()) {
            return;
        }
        this.executor.execute(() -> {
            try {
                final var groupsByPoint =
                        new LinkedHashMap<ImmutablePoint2, VirtualBuilding.Group<? extends MindustryImage>>();
                for (final var group : groups) {
                    groupsByPoint.put(new ImmutablePoint2(group.x(), group.y()), group);
                }

                final var request = MutableRequest.POST(
                                resolve(this.config.endpoint(), "v4/classify"),
                                MoreBodyPublishers.ofOutputStream(out -> this.codec.encode(out, groups), this.executor),
                                MediaType.APPLICATION_OCTET_STREAM)
                        .timeout(this.config.timeout());
                this.config.token().ifPresent(token -> request.header("Authorization", "Bearer " + token));

                final var response =
                        this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new IllegalStateException(
                            "Remote detector returned " + response.statusCode() + ": " + response.body());
                }
                final var batch = Objects.requireNonNull(
                        this.gson.fromJson(response.body(), DetectionBatchResponse.class),
                        "Missing detection batch response");

                final var events = new HashMap<ImmutablePoint2, ClassificationEvent>();
                for (final var entry : batch.classifications().entrySet()) {
                    final var point = new ImmutablePoint2(Point2.x(entry.getKey()), Point2.y(entry.getKey()));
                    if (events.containsKey(point)) {
                        logger.trace("Received duplicate classifications for group at {}", point);
                        continue;
                    }
                    final var group = groupsByPoint.get(point);
                    if (group == null) {
                        logger.trace("Received invalid group (at {}) from api", point);
                        continue;
                    }
                    events.put(point, new ClassificationEvent(entry.getValue().classification(), group));
                }

                Core.app.post(() -> events.values().forEach(Events::fire));
            } catch (final Exception e) {
                logger.error("Remote classifications failed", e);
            }
        });
    }

    private static URI resolve(final URI endpoint, final String path) {
        final var base = endpoint.toString();
        final var normalizedBase = base.endsWith("/") ? base : base + "/";
        return URI.create(normalizedBase).resolve(path);
    }
}
