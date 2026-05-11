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
import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

final class DiscordWebhook implements LifecycleListener {

    private static final MiniLogger log = MiniLogger.forClass(DiscordWebhook.class);

    private final Methanol http;
    private final MonoRateLimiter rateLimiter = new MonoRateLimiter(Duration.ofSeconds(1));
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("nohorny-discord-webhook-", 0).factory());

    private final Supplier<String> webhook = MindustryUtils.registerSafeSettingEntry(
            "nohorny-discord-webhook",
            "A basic discord webhook to send WARN and NSFW classifications.",
            "",
            value -> value.isBlank() ? "" : URI.create(value).toString(),
            this::onWebhookConfigure);

    DiscordWebhook(final Methanol http) {
        this.http = http;
    }

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
                this.send(URI.create(webhook), this.createClassificationFormPayload(event));
            } catch (final Exception e) {
                log.error(
                        "Failed to send Discord warning for group at ({}, {})",
                        event.group().x(),
                        event.group().y(),
                        e);
            }
        });
    }

    private void send(final URI webhook, final MultipartBodyPublisher form) throws Exception {
        this.rateLimiter.waitIfRateLimited();
        final var response = this.http.send(
                MutableRequest.POST(webhook, form).timeout(Duration.ofSeconds(15L)),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IOException(
                    "Discord webhook returned http code " + response.statusCode() + ": " + response.body());
        }
    }

    private void onWebhookConfigure() {
        final var webhook = this.webhook.get();
        if (webhook.isBlank()) {
            return;
        }
        this.executor.execute(() -> {
            try {
                this.send(URI.create(webhook), this.createConfigurationSuccessFormPayload());
            } catch (final Exception e) {
                log.error("Failed to test the Discord webhook", e);
            }
        });
    }

    private MultipartBodyPublisher createConfigurationSuccessFormPayload() {
        return MultipartBodyPublisher.newBuilder()
                .textPart(
                        "payload_json",
                        this.createEmbedJsonPayload(
                                        "NoHorny has successfuly been configured",
                                        "NSFW alerts will now be sent here",
                                        null,
                                        null)
                                .toString())
                .build();
    }

    private MultipartBodyPublisher createClassificationFormPayload(final ClassificationEvent event) {
        final var imageName = "SPOILER_nohorny_image_" + System.currentTimeMillis() + ".png";
        return MultipartBodyPublisher.newBuilder()
                .textPart(
                        "payload_json",
                        this.createClassificationJsonPayload(event, "attachment://" + imageName)
                                .toString())
                .formPart(
                        "files[0]",
                        imageName,
                        MoreBodyPublishers.ofMediaType(
                                MoreBodyPublishers.ofOutputStream(
                                        stream -> ImageIO.write(
                                                MindustryImageRenderer.render(event.group()), "png", stream),
                                        this.executor),
                                MediaType.IMAGE_PNG))
                .build();
    }

    private Jval createClassificationJsonPayload(final ClassificationEvent event, final String image) {
        final var message = new StringBuilder();
        if (event.author() == null) {
            message.append("- Author: **unknown**\n");
        } else {
            message.append("- Author: **`")
                    .append(event.author().uuid())
                    .append("`/`")
                    .append(event.author().ip())
                    .append("`**\n");
        }
        message.append("- Coordinates: **(")
                .append(event.group().x())
                .append(", ")
                .append(event.group().y())
                .append(")**\n");
        message.append("- Rating: **").append(event.response().rating()).append("**\n");
        message.append("- Confidence: **")
                .append((int) (event.response().confidence() * 100))
                .append("%**\n");
        return this.createEmbedJsonPayload(
                "NoHorny has detected unsafe buildings",
                message.toString(),
                image,
                event.response().identifier());
    }

    private Jval createEmbedJsonPayload(
            final String title, final String content, final @Nullable String image, final @Nullable String footer) {
        final var embed = Jval.newObject()
                .put("color", Color.PINK.getRGB() & 0xFFFFFF)
                .put("title", title)
                .put("description", content);
        if (image != null) {
            embed.put("image", Jval.newObject().put("url", image));
        }
        if (footer != null) {
            embed.put("footer", Jval.newObject().put("text", footer));
        }
        return Jval.newObject()
                .put("allowed_mentions", Jval.newObject().put("parse", Jval.newArray()))
                .put("embeds", Jval.newArray().add(embed));
    }
}
