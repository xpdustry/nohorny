// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import arc.Events;
import arc.mock.MockApplication;
import arc.mock.MockSettings;
import arc.util.serialization.Jval;
import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.ImmutableByteArray;
import com.xpdustry.nohorny.common.ImmutableIntArray;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// AIGEN
@EnabledIfEnvironmentVariable(named = "NOHORNY_TEST_DISCORD_WEBHOOK", matches = ".+")
final class DiscordWebhookTest {

    @TempDir
    Path directory;

    private URI webhookUri;
    private DiscordWebhook webhook;
    private boolean webhookClosed;

    @BeforeEach
    void before() {
        Core.app = new MockApplication();
        Core.settings = new MockSettings();
        Events.clear();
        this.webhookUri = URI.create(System.getenv("NOHORNY_TEST_DISCORD_WEBHOOK"));
        NoHornySetting.DISCORD_WEBHOOK.set(this.webhookUri);
        NoHornySetting.DISCORD_WEBHOOK_MESSAGE_RETENTION.set(0);
        this.webhook = new DiscordWebhook(this.directory, "NoHorny DiscordWebhookTest");
        this.webhook.onInit();
    }

    @AfterEach
    void after() {
        this.closeWebhook();
        Events.clear();
        Core.settings = null;
        Core.app = null;
    }

    @Test
    void send_record_and_delete_expired_classification_report() throws Exception {
        Events.fire(classification(Rating.WARN, "discord-webhook-test"));

        final var report = awaitRecordedReport();
        try {
            final var response = getReport(report);
            assertEquals(200, response.statusCode());
            final var message = Jval.read(response.body());
            assertEquals(1, message.get("components").asArray().size);
            assertTrue(response.body().contains("discord-webhook-test"));
            assertTrue(response.body().contains("(10, 20)"));
        } finally {
            NoHornySetting.DISCORD_WEBHOOK.set(null);
            this.webhook.deleteExpiredReports();
        }

        assertTrue(awaitReportDeletion(report), "Expected the expired Discord report to be deleted");
    }

    @Test
    void ignore_safe_classification() {
        Events.fire(classification(Rating.SAFE, "safe-discord-webhook-test"));
        this.closeWebhook();

        assertFalse(Files.exists(this.directory.resolve("reports.jsonl")));
    }

    private ClassificationEvent classification(final Rating rating, final String identifier) {
        return new ClassificationEvent(
                new VirtualBuilding.Group<>(
                        10,
                        20,
                        1,
                        1,
                        List.of(new VirtualBuilding<>(
                                10,
                                20,
                                1,
                                new MindustryCanvas(
                                        1,
                                        ImmutableIntArray.wrap(0xFF69B4FF),
                                        ImmutableByteArray.wrap((byte) 0),
                                        null)))),
                null,
                new ClassificationResponse("test", rating, 0.99D, identifier));
    }

    private long awaitRecordedReport() throws Exception {
        final var reports = this.directory.resolve("reports.jsonl");
        for (int attempt = 0; attempt < 150; attempt++) {
            if (Files.exists(reports)) {
                final var content = Files.readString(reports).trim();
                if (!content.isEmpty()) {
                    return Long.parseLong(Jval.read(content).getString("message_id"));
                }
            }
            TimeUnit.MILLISECONDS.sleep(100L);
        }
        throw new AssertionError("Discord report was not recorded within 15 seconds");
    }

    private boolean awaitReportDeletion(final long report) throws Exception {
        for (int attempt = 0; attempt < 150; attempt++) {
            if (getReport(report).statusCode() == 404) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(100L);
        }
        return false;
    }

    private HttpResponse<String> getReport(final long report) throws Exception {
        try (final var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(reportUri(report))
                            .timeout(Duration.ofSeconds(15L))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private URI reportUri(final long report) {
        return URI.create(this.webhookUri + "/messages/" + report);
    }

    private void closeWebhook() {
        if (!this.webhookClosed) {
            this.webhook.onExit();
            this.webhookClosed = true;
        }
    }
}
