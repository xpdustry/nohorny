// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.util.serialization.Jval;
import com.github.mizosoft.methanol.MediaType;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MoreBodyPublishers;
import com.github.mizosoft.methanol.MultipartBodyPublisher;
import com.github.mizosoft.methanol.MutableRequest;
import com.xpdustry.nohorny.common.MindustryImageRenderer;
import com.xpdustry.nohorny.common.MonoRateLimiter;
import com.xpdustry.nohorny.common.Rating;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

final class DiscordWebhook implements LifecycleListener {

    private static final MiniLogger log = MiniLogger.forClass(DiscordWebhook.class);

    private final MonoRateLimiter rateLimiter = new MonoRateLimiter(Duration.ofSeconds(1));
    private final Methanol http = Methanol.create();
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("nohorny-discord-webhook-", 0).factory());

    private final Supplier<String> webhook = MindustryUtils.registerSafeSettingEntry(
            "nohorny-discord-webhook",
            "A basic discord webhook to send WARN and NSFW classifications.",
            "",
            value -> value.isBlank() ? "" : URI.create(value).toString());

    @Override
    public void onInit() {
        MindustryUtils.onEvent(ClassificationEvent.class, this::onClassificationEvent);
    }

    @Override
    public void onExit() {
        this.executor.close();
    }

    private void onClassificationEvent(final ClassificationEvent event) {
        if (!event.response().rating().isWorseOrEqualThan(Rating.WARN)) {
            return;
        }
        final var webhook = this.webhook.get();
        if (webhook.isBlank()) {
            return;
        }
        this.executor.execute(() -> {
            try {
                this.sendWarning(URI.create(webhook), event);
            } catch (final Exception e) {
                log.error(
                        "Failed to send Discord warning for group at ({}, {})",
                        event.group().x(),
                        event.group().y(),
                        e);
            }
        });
    }

    private void sendWarning(final URI webhook, final ClassificationEvent event) throws Exception {
        this.rateLimiter.waitIfRateLimited();
        final var multipart = MultipartBodyPublisher.newBuilder()
                .textPart("payload_json", this.payload(event).toString())
                .formPart(
                        "files[0]",
                        "SPOILER_nohorny_image_" + System.currentTimeMillis() + ".png",
                        MoreBodyPublishers.ofMediaType(
                                MoreBodyPublishers.ofOutputStream(
                                        stream -> ImageIO.write(
                                                MindustryImageRenderer.render(event.group()), "png", stream),
                                        this.executor),
                                MediaType.IMAGE_PNG))
                .build();
        final var response = this.http.send(
                MutableRequest.POST(webhook, multipart).timeout(Duration.ofSeconds(15L)),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            log.error(
                    "Discord webhook returned http code {} for group at ({}, {}): {}",
                    response.statusCode(),
                    event.group().x(),
                    event.group().y(),
                    response.body());
        }
    }

    private Jval payload(final ClassificationEvent event) {
        final var message = new StringBuilder();
        message.append("**NoHorny detected unsafe buildings at (")
                .append(event.group().x())
                .append(", ")
                .append(event.group().y())
                .append("):**\n");

        if (event.author() == null) {
            message.append("- Author: **unknown**\n");
        } else {
            message.append("- Author: **`")
                    .append(event.author().uuid())
                    .append("`/`")
                    .append(event.author().ip())
                    .append("`**\n");
        }

        message.append("- Rating: **").append(event.response().rating()).append("**\n");
        message.append("- Confidence: **")
                .append((int) (event.response().confidence() * 100))
                .append("%**\n");
        message.append("-# Trace ID: **`").append(event.response().identifier()).append("`**");

        return Jval.newObject()
                .put("content", message.toString())
                .put("allowed_mentions", Jval.newObject().put("parse", Jval.newArray()));
    }
}
