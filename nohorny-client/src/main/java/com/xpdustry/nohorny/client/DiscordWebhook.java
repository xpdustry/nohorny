// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import arc.util.Strings;
import arc.util.Timer;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import mindustry.Vars;
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

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("nohorny-discord-webhook-", 0).factory());

    private final String userAgent;

    private final ProxyScrapeProxySelector proxy = new ProxyScrapeProxySelector(this.executor);
    private final HttpClient http =
            HttpClient.newBuilder().executor(this.executor).proxy(this.proxy).build();
    private final MonoRateLimiter rateLimiter = new MonoRateLimiter(Duration.ofSeconds(1));

    private final Path directory;
    private final Object lock = new Object();
    private Timer.@Nullable Task cleanupTask = null;

    DiscordWebhook(final Path directory) {
        final var metadata = Vars.mods.getMod(NoHornyPlugin.class).meta;
        this.directory = directory;
        this.userAgent = "NoHorny (https://github/" + metadata.repo + ", v" + metadata.version + ")";
    }

    DiscordWebhook(final Path directory, final String userAgent) {
        this.directory = directory;
        this.userAgent = userAgent;
    }

    @Override
    public void onInit() {
        MindustryUtils.onEvent(ClassificationEvent.class, this::onClassificationEvent);

        MindustryUtils.onEvent(SettingChangeEvent.class, event -> {
            if (!(event.key().equals(NoHornySetting.DISCORD_WEBHOOK)
                    || event.key().equals(NoHornySetting.DISCORD_WEBHOOK_NAME)
                    || event.key().equals(NoHornySetting.DISCORD_WEBHOOK_PROXY_ENABLED))) {
                return;
            }
            this.executor.execute(() -> {
                final var webhook = NoHornySetting.DISCORD_WEBHOOK.get();
                if (webhook == null) {
                    return;
                }
                if (Boolean.TRUE.equals(NoHornySetting.DISCORD_WEBHOOK_PROXY_ENABLED.get())) {
                    this.proxy.refresh(true);
                }
                if (event.key().equals(NoHornySetting.DISCORD_WEBHOOK)) {
                    this.onWebhookConfigure(webhook, "NSFW alerts will now be sent here.");
                } else if (event.key().equals(NoHornySetting.DISCORD_WEBHOOK_NAME)) {
                    this.onWebhookConfigure(
                            webhook,
                            "The webhook username has been set to " + NoHornySetting.DISCORD_WEBHOOK_NAME.get() + ".");
                }
            });
        });

        this.executor.execute(() -> {
            if (Boolean.TRUE.equals(NoHornySetting.DISCORD_WEBHOOK_PROXY_ENABLED.get())) {
                this.proxy.refresh(true);
            }
        });

        this.cleanupTask = Timer.schedule(() -> this.executor.execute(this::deleteExpiredReports), 60F, 6F * 60F * 60F);
    }

    @Override
    public void onExit() {
        Objects.requireNonNull(this.cleanupTask, "cleanup-task").cancel();
        this.executor.close();
        this.http.close();
        this.proxy.close();
    }

    private void onWebhookConfigure(final URI webhook, final String message) {
        try {
            this.send(webhook, this.createConfigurationSuccessFormPayload(message));
        } catch (final Exception e) {
            log.error("Failed to test the Discord webhook", e);
        }
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
                final var id = this.send(webhook, this.createClassificationFormPayload(event));
                this.recordReport(webhook, id);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final IOException | URISyntaxException e) {
                log.error(
                        "Failed to send Discord warning for group at ({}, {})",
                        event.group().x(),
                        event.group().y(),
                        e);
            }
        });
    }

    private long send(final URI webhook, final MultipartFormBodyPublisher form)
            throws IOException, InterruptedException, URISyntaxException {
        this.rateLimiter.waitIfRateLimited();
        final var response = this.http.send(
                HttpRequest.newBuilder(this.withWebhookQueryParameters(webhook))
                        .timeout(Duration.ofSeconds(15L))
                        .POST(form)
                        .header("User-Agent", this.userAgent)
                        .header("Content-Type", form.contentType())
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IOException(
                    "Discord webhook returned http code " + response.statusCode() + ": " + response.body());
        }
        final var id = Strings.parseLong(Jval.read(response.body()).getString("id", "-1"), -1);
        if (id == -1) {
            throw new IOException("Failed to extract the created webhook message id from discord: " + response.body());
        }
        return id;
    }

    private URI withWebhookQueryParameters(final URI webhook) throws URISyntaxException {
        final var query = webhook.getQuery();
        final var parameters = "with_components=true&wait=true";
        return new URI(
                webhook.getScheme(),
                webhook.getUserInfo(),
                webhook.getHost(),
                webhook.getPort(),
                webhook.getPath(),
                query == null ? parameters : query + "&" + parameters,
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

    void deleteExpiredReports() {
        if (NoHornySetting.DISCORD_WEBHOOK_MESSAGE_RETENTION.get() == null) {
            return;
        }
        final var src = this.directory.resolve("reports.jsonl");
        final var tmp = this.directory.resolve("reports.jsonl.tmp");
        synchronized (this.lock) {
            if (Files.notExists(src)) {
                return;
            }

            try (final var reader = Files.newBufferedReader(src);
                    final var writer = Files.newBufferedWriter(
                            tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    final var report = this.readReport(line);
                    if (report == null) {
                        continue;
                    }
                    if (report.deleteAt().isAfter(Instant.now())
                            || !this.deleteReport(report.webhook(), report.messageId())) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            } catch (final IOException e1) {
                log.error("Failed to read the sent NoHorny discord reports from the settings", e1);
                try {
                    Files.deleteIfExists(tmp);
                } catch (final IOException e2) {
                    log.error("Failed to delete incomplete Discord report state", e2);
                }
                return;
            }

            try {
                Files.move(tmp, src, StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException e) {
                log.error("Failed to save processed reports", e);
            }
        }
    }

    private void recordReport(final URI webhook, final long report) {
        final var retention = NoHornySetting.DISCORD_WEBHOOK_MESSAGE_RETENTION.get();
        if (retention == null) {
            return;
        }
        synchronized (this.lock) {
            try (final var writer = Files.newBufferedWriter(
                    this.directory.resolve("reports.jsonl"), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(Jval.newObject()
                        .put("message_id", Long.toString(report))
                        .put(
                                "delete_at",
                                Instant.now().plus(retention, ChronoUnit.DAYS).toEpochMilli())
                        .put("webhook", webhook.toString())
                        .toString());
                writer.newLine();
            } catch (final IOException e) {
                log.error("Failed to schedule webhook message {} for deletion", report, e);
            }
        }
    }

    private boolean deleteReport(final URI webhook, final long report) {
        try {
            this.rateLimiter.waitIfRateLimited();
            final var response = this.http.send(
                    HttpRequest.newBuilder(HttpUtils.appendPathSegments(webhook, "messages", Long.toString(report)))
                            .timeout(Duration.ofSeconds(15L))
                            .DELETE()
                            .header("User-Agent", this.userAgent)
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() <= 299) {
                log.debug("Deleted the expired NoHorny discord report {}", report);
                return true;
            }
            if (response.statusCode() == 429) {
                log.debug("Report {} deletion failed due to rate limit, retrying later.", report);
                return false;
            }
            if (response.statusCode() >= 400 && response.statusCode() <= 499) {
                log.debug(
                        "Discarding the NoHorny discord report {} that can no longer be deleted (http-code={})",
                        report,
                        response.statusCode());
                return true;
            }
            log.error(
                    "Discord webhook returned http code {} while deleting the report {}",
                    response.statusCode(),
                    report);
        } catch (final InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (final IOException e) {
            log.error("Failed to delete the NoHorny discord report {}", report, e);
        }
        return false;
    }

    private @Nullable Report readReport(final String line) {
        try {
            final var json = Jval.read(line);
            final var messageId = Strings.parseLong(json.getString("message_id", "-1"), -1);
            final var deleteAt = json.getLong("delete_at", -1L);
            if (messageId == -1L || deleteAt == -1L) {
                return null;
            }
            return new Report(messageId, Instant.ofEpochMilli(deleteAt), URI.create(json.getString("webhook")));
        } catch (final RuntimeException _) {
            return null;
        }
    }

    private record Report(long messageId, Instant deleteAt, URI webhook) {}
}
