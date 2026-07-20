// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import arc.util.serialization.Jval;
import com.xpdustry.nohorny.common.MindustryImageRenderer;
import com.xpdustry.nohorny.common.MonoRateLimiter;
import com.xpdustry.nohorny.common.Rating;
import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import mindustry.Vars;
import mindustry.mod.Mods;
import org.jspecify.annotations.Nullable;

// https://docs.discord.com/developers/resources/webhook#execute-webhook
// https://docs.discord.com/developers/components/reference
final class DiscordWebhook implements LifecycleListener {

    private static final MiniLogger log = MiniLogger.forClass(DiscordWebhook.class);

    private static final int COMPONENT_TYPE_TEXT_DISPLAY = 10;
    private static final int COMPONENT_TYPE_MEDIA_GALLERY = 12;
    private static final int COMPONENT_TYPE_SEPARATOR = 14;
    private static final int COMPONENT_TYPE_CONTAINER = 17;
    private static final int MESSAGE_FLAG_IS_COMPONENTS_V2 = 1 << 15;

    private final HttpClient http;
    private final Mods.ModMeta metadata = Vars.mods.getMod(NoHornyPlugin.class).meta;
    private final MonoRateLimiter rateLimiter = new MonoRateLimiter(Duration.ofSeconds(1));
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("nohorny-discord-webhook-", 0).factory());

    DiscordWebhook(final HttpClient http) {
        this.http = http;
        MindustryUtils.onEvent(SettingChangeEvent.class, event -> {
            if (event.key().equals(NoHornySetting.DISCORD_WEBHOOK)) {
                this.onWebhookConfigure("NSFW alerts will now be sent here.");
            } else if (event.key().equals(NoHornySetting.DISCORD_WEBHOOK_NAME)) {
                this.onWebhookConfigure(
                        "The webhook username has been set to " + NoHornySetting.DISCORD_WEBHOOK_NAME.get() + ".");
            }
        });
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
        final var webhook = NoHornySetting.DISCORD_WEBHOOK.get();
        if (webhook == null) {
            return;
        }
        this.executor.execute(() -> {
            try {
                this.send(webhook, this.createClassificationFormPayload(event));
            } catch (final Exception e) {
                log.error(
                        "Failed to send Discord warning for group at ({}, {})",
                        event.group().x(),
                        event.group().y(),
                        e);
            }
        });
    }

    private void onWebhookConfigure(final String message) {
        final var webhook = NoHornySetting.DISCORD_WEBHOOK.get();
        if (webhook == null) {
            return;
        }
        this.executor.execute(() -> {
            try {
                this.send(webhook, this.createConfigurationSuccessFormPayload(message));
            } catch (final Exception e) {
                log.error("Failed to test the Discord webhook", e);
            }
        });
    }

    private void send(final URI webhook, final MultipartFormBodyPublisher form) throws Exception {
        this.rateLimiter.waitIfRateLimited();
        final var response = this.http.send(
                HttpRequest.newBuilder(this.withComponentsV2Enabled(webhook))
                        .timeout(Duration.ofSeconds(15L))
                        .POST(form)
                        .header(
                                "User-Agent",
                                "NoHorny (https://github/" + metadata.repo + ", v" + metadata.version + ")")
                        .header("Content-Type", form.contentType())
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IOException(
                    "Discord webhook returned http code " + response.statusCode() + ": " + response.body());
        }
    }

    private URI withComponentsV2Enabled(final URI webhook) throws URISyntaxException {
        final var query = webhook.getQuery();
        return new URI(
                webhook.getScheme(),
                webhook.getUserInfo(),
                webhook.getHost(),
                webhook.getPort(),
                webhook.getPath(),
                query == null ? "with_components=true" : query + "&with_components=true",
                webhook.getFragment());
    }

    private MultipartFormBodyPublisher createConfigurationSuccessFormPayload(final String message) {
        return new MultipartFormBodyPublisher.Builder()
                .textPart(
                        "payload_json",
                        this.createComponentsJsonPayload("NoHorny has been re-configured", message, null, null)
                                .toString())
                .build();
    }

    private MultipartFormBodyPublisher createClassificationFormPayload(final ClassificationEvent event) {
        final var imageName = "SPOILER_nohorny_image_" + System.currentTimeMillis() + ".png";
        return new MultipartFormBodyPublisher.Builder()
                .textPart(
                        "payload_json",
                        this.createClassificationJsonPayload(event, "attachment://" + imageName)
                                .toString())
                .formPart(
                        "files[0]",
                        imageName,
                        "image/png",
                        HttpUtils.ofOutputStream(
                                this.executor,
                                stream -> ImageIO.write(MindustryImageRenderer.render(event.group()), "png", stream)))
                .build();
    }

    private Jval createClassificationJsonPayload(final ClassificationEvent event, final String image) {
        final var message = new StringBuilder();
        final var author = event.author();
        if (author == null) {
            message.append("- Author: **unknown**\n");
        } else {
            final var name = CompletableFuture.supplyAsync(
                            () -> {
                                final var info = Vars.netServer.admins.getInfoOptional(author.uuid());
                                return info == null ? "unknown" : info.plainLastName();
                            },
                            Core.app::post)
                    .orTimeout(1, TimeUnit.SECONDS)
                    .exceptionally(_ -> "unknown")
                    .join();
            message.append("- Author Name: **").append(name).append("**\n");
            message.append("- Author UUID: **`").append(author.uuid()).append("`**\n");
            message.append("- Author IP: **`").append(author.ip()).append("`**\n");
        }
        message.append("- Coordinates: **(")
                .append(event.group().x())
                .append(", ")
                .append(event.group().y())
                .append(")**\n");
        message.append("- Rating: **").append(event.response().rating()).append("**\n");
        message.append("- Confidence: **")
                .append((int) Math.ceil(event.response().confidence() * 100))
                .append("%**\n");
        return this.createComponentsJsonPayload(
                "NoHorny has detected unsafe buildings",
                message.toString(),
                image,
                "Request ID: `" + event.response().identifier() + "`");
    }

    private Jval createComponentsJsonPayload(
            final String title, final String content, final @Nullable String image, final @Nullable String footer) {
        final var components = Jval.newArray()
                .add(Jval.newObject().put("type", COMPONENT_TYPE_TEXT_DISPLAY).put("content", "## " + title))
                .add(Jval.newObject()
                        .put("type", COMPONENT_TYPE_SEPARATOR)
                        .put("divider", true)
                        .put("spacing", 1))
                .add(Jval.newObject().put("type", COMPONENT_TYPE_TEXT_DISPLAY).put("content", content));
        if (image != null) {
            components.add(Jval.newObject()
                    .put("type", COMPONENT_TYPE_SEPARATOR)
                    .put("divider", false)
                    .put("spacing", 1));
            components.add(Jval.newObject()
                    .put("type", COMPONENT_TYPE_MEDIA_GALLERY)
                    .put(
                            "items",
                            Jval.newArray()
                                    .add(Jval.newObject()
                                            .put("media", Jval.newObject().put("url", image))
                                            .put("spoiler", true))));
        }
        if (footer != null) {
            components.add(Jval.newObject()
                    .put("type", COMPONENT_TYPE_SEPARATOR)
                    .put("divider", false)
                    .put("spacing", 1));
            components.add(
                    Jval.newObject().put("type", COMPONENT_TYPE_TEXT_DISPLAY).put("content", footer));
        }
        final var payload = Jval.newObject()
                .put("flags", MESSAGE_FLAG_IS_COMPONENTS_V2)
                .put("allowed_mentions", Jval.newObject().put("parse", Jval.newArray()))
                .put(
                        "components",
                        Jval.newArray()
                                .add(Jval.newObject()
                                        .put("type", COMPONENT_TYPE_CONTAINER)
                                        .put("accent_color", Color.PINK.getRGB() & 0xFFFFFF)
                                        .put("spoiler", false)
                                        .put("components", components)));
        final var username = NoHornySetting.DISCORD_WEBHOOK_NAME.get();
        if (username != null) {
            payload.put("username", username);
        }
        return payload;
    }
}
