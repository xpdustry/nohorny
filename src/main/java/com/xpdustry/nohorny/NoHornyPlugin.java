// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny;

import arc.ApplicationListener;
import arc.Core;
import com.github.mizosoft.methanol.Methanol;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.xpdustry.nohorny.authentication.AllowAllAuthenticator;
import com.xpdustry.nohorny.authentication.ApiKeyAuthenticator;
import com.xpdustry.nohorny.authentication.Authenticator;
import com.xpdustry.nohorny.authentication.AuthenticatorConfig;
import com.xpdustry.nohorny.authentication.LocalhostAuthenticator;
import com.xpdustry.nohorny.authentication.MindustryServerAuthenticator;
import com.xpdustry.nohorny.classification.Classifier;
import com.xpdustry.nohorny.classification.ClassifierConfig;
import com.xpdustry.nohorny.classification.SightEngineClassifier;
import com.xpdustry.nohorny.classification.ViTClassifier;
import com.xpdustry.nohorny.config.FriendlyDurationDecoder;
import com.xpdustry.nohorny.config.InetAddressDecoder;
import com.xpdustry.nohorny.config.NoHornyConfig;
import com.xpdustry.nohorny.config.SealedConfigDecoder;
import com.xpdustry.nohorny.config.SnakeYamlLoader;
import com.xpdustry.nohorny.geometry.ImmutablePoint2;
import com.xpdustry.nohorny.image.ImageBinaryCodec;
import com.xpdustry.nohorny.image.ImageRenderer;
import com.xpdustry.nohorny.tracking.AutoModerator;
import com.xpdustry.nohorny.tracking.CanvasTracker;
import com.xpdustry.nohorny.tracking.DisplayTracker;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import mindustry.Vars;
import mindustry.mod.Plugin;
import org.github.gestalt.config.builder.GestaltBuilder;
import org.github.gestalt.config.decoder.BooleanDecoder;
import org.github.gestalt.config.decoder.DoubleDecoder;
import org.github.gestalt.config.decoder.EnumDecoder;
import org.github.gestalt.config.decoder.FloatDecoder;
import org.github.gestalt.config.decoder.IntegerDecoder;
import org.github.gestalt.config.decoder.ListDecoder;
import org.github.gestalt.config.decoder.OptionalDecoder;
import org.github.gestalt.config.decoder.RecordDecoder;
import org.github.gestalt.config.decoder.StringDecoder;
import org.github.gestalt.config.decoder.URIDecoder;
import org.github.gestalt.config.path.mapper.KebabCasePathMapper;
import org.github.gestalt.config.source.ClassPathConfigSourceBuilder;
import org.github.gestalt.config.source.FileConfigSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoHornyPlugin extends Plugin {

    private static final Logger logger = LoggerFactory.getLogger(NoHornyPlugin.class);

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors() * 2,
            Thread.ofVirtual().name("nohorny-worker-thread-", 0).factory());
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ImmutablePoint2.class, new ImmutablePoint2.TypeAdapter())
            .setStrictness(Strictness.STRICT)
            .create();
    private final Methanol http = Methanol.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .executor(this.executor)
            .build();
    private final Path directory = Vars.mods.getConfigFolder(this).file().toPath();
    private final List<NoHornyListener> listeners = new ArrayList<>();

    @Override
    public void init() {
        try {
            Files.createDirectories(this.directory);

            final var config = this.loadConfig();
            final var codec = new ImageBinaryCodec();
            final var renderer = new ImageRenderer();

            if (config.server().isPresent()) {
                final var server = config.server().orElseThrow();
                final var classifiers = server.classifiers().stream()
                        .map(this::createClassifier)
                        .toList();
                this.listeners.addAll(classifiers);
                final var authenticators = server.authenticators().stream()
                        .map(this::createAuthenticator)
                        .toList();
                this.listeners.addAll(authenticators);
                this.listeners.add(new NoHornyServer(
                        server, this.executor, this.gson, classifiers, authenticators, renderer, codec));
            }

            if (config.client().isPresent()) {
                final var clientConfig = config.client().orElseThrow();
                final var client = new NoHornyClient(this.executor, this.http, this.gson, clientConfig, codec);
                this.listeners.add(client);
                this.listeners.add(new AutoModerator(clientConfig.autoMod()));
                this.listeners.add(new DisplayTracker(this.executor, client, clientConfig.displays()));
                this.listeners.add(new CanvasTracker(this.executor, client, clientConfig.canvases()));
            }

            int initializedCount = 0;
            try {
                for (; initializedCount < this.listeners.size(); initializedCount++) {
                    this.listeners.get(initializedCount).onInit();
                }
            } catch (final Exception e) {
                this.closeListeners(this.listeners, initializedCount - 1);
                NoHornyPlugin.this.executor.shutdown();
                throw e;
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to initialize nohorny", e);
        }

        logger.info("NoHorny plugin initialized");

        Core.app.addListener(new ApplicationListener() {

            @Override
            public void dispose() {
                closeListeners(NoHornyPlugin.this.listeners, NoHornyPlugin.this.listeners.size() - 1);
                NoHornyPlugin.this.http.shutdown();
                NoHornyPlugin.this.executor.shutdown();
            }
        });
    }

    private void closeListeners(final List<NoHornyListener> listeners, final int lastIndex) {
        for (int i = lastIndex; i >= 0; i--) {
            try {
                listeners.get(i).onExit();
            } catch (final Throwable e) {
                logger.error("Failed to close {}", listeners.get(i), e);
            }
        }
    }

    private Classifier createClassifier(final ClassifierConfig config) {
        return switch (config) {
            case ClassifierConfig.ViT vit ->
                new ViTClassifier(vit.labels(), vit.nsfwLabel(), this.directory.resolve(vit.file()), vit.thresholds());
            case ClassifierConfig.SightEngine se ->
                new SightEngineClassifier(this.http, this.gson, this.executor, se.user(), se.secret(), se.thresholds());
        };
    }

    private Authenticator createAuthenticator(final AuthenticatorConfig config) {
        return switch (config) {
            case AuthenticatorConfig.AllowAll ignored -> new AllowAllAuthenticator();
            case AuthenticatorConfig.Localhost ignored -> new LocalhostAuthenticator();
            case AuthenticatorConfig.ApiKey ignored ->
                new ApiKeyAuthenticator(this.directory.resolve("api-keys.json"), this.gson);
            case AuthenticatorConfig.MindustryServerList msl ->
                new MindustryServerAuthenticator(
                        this.http, this.gson, msl.sources(), msl.refreshInterval(), this.executor);
        };
    }

    private NoHornyConfig loadConfig() throws Exception {
        final var builder = new GestaltBuilder()
                .addSource(ClassPathConfigSourceBuilder.builder()
                        .setResource("com/xpdustry/nohorny/default-config.yaml")
                        .build())
                .setTreatMissingDiscretionaryValuesAsErrors(false)
                .addPathMapper(new KebabCasePathMapper())
                .addConfigLoader(new SnakeYamlLoader())
                .addDecoder(new RecordDecoder())
                .addDecoder(new URIDecoder())
                .addDecoder(new BooleanDecoder())
                .addDecoder(new DoubleDecoder())
                .addDecoder(new FriendlyDurationDecoder())
                .addDecoder(new EnumDecoder<>())
                .addDecoder(new FloatDecoder())
                .addDecoder(new IntegerDecoder())
                .addDecoder(new OptionalDecoder())
                .addDecoder(new StringDecoder())
                .addDecoder(new SealedConfigDecoder())
                .addDecoder(new ListDecoder())
                .addDecoder(new InetAddressDecoder());
        final var file = this.directory.resolve("config.yaml");
        if (Files.exists(file)) {
            builder.addSource(FileConfigSourceBuilder.builder().setPath(file).build());
        }
        final var gestalt = builder.build();
        gestalt.loadConfigs();
        return gestalt.getConfig("", NoHornyConfig.class);
    }
}
