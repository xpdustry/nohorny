// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.lifecycle.LifecycleManager;
import com.xpdustry.nohorny.server.config.GestaltConfigSupplier;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class Launcher {

    private static final String CONFIG_FILE_PATH_ENV = "NH_CONFIG_FILE_PATH";
    private static final Path DEFAULT_CONFIG_FILE = Path.of("config.json");

    private Launcher() {}

    static void main() throws Exception {
        final var configSupplier = new GestaltConfigSupplier<>(Config.class, getConfigFilePath());
        configSupplier.reload();
        final var config = configSupplier.get();
        final var lifecycle = new LifecycleManager();
        final var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        final var knows = new KnowYourClient(httpClient);
        lifecycle.addListener(knows);
        final var classifierConfig = config.classifier();
        final var serverConfig = config.server();
        final var cache = Path.of(".nohorny-model-cache");
        if (Files.notExists(cache)) {
            Files.createDirectories(cache);
        }
        final var classifier = new ViTClassifier(
                classifierConfig.repository(),
                classifierConfig.revision().orElse("main"),
                classifierConfig.file(),
                classifierConfig.token().orElse(null),
                classifierConfig.labels(),
                classifierConfig.nsfwLabel(),
                httpClient,
                cache,
                classifierConfig.thresholds(),
                classifierConfig.engine().orElse(ViTClassifier.Engine.PYTORCH));
        lifecycle.addListener(classifier);
        final var httpServer = new NoHornyServer(
                serverConfig.host().orElse("localhost"), serverConfig.port().orElse(8080), classifier, knows);
        lifecycle.addListener(httpServer);

        Runtime.getRuntime().addShutdownHook(new Thread(lifecycle::exit, "nohorny-shutdown"));

        lifecycle.init();
        Thread.currentThread().join();
    }

    private static Path getConfigFilePath() {
        return Path.of(Optional.ofNullable(System.getenv(CONFIG_FILE_PATH_ENV)).orElse(DEFAULT_CONFIG_FILE.toString()));
    }

    public record Config(ServerConfig server, ViTClassifierConfig classifier) {}

    public record ServerConfig(Optional<String> host, Optional<Integer> port) {}

    public record ViTClassifierConfig(
            String repository,
            Optional<String> revision,
            String file,
            Optional<String> token,
            List<String> labels,
            String nsfwLabel,
            Thresholds thresholds,
            Optional<ViTClassifier.Engine> engine) {}
}
