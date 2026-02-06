// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.authentication;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mindustry.server.ServerControl;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApiKeyAuthenticator implements Authenticator {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticator.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KEY_PREFIX = "nhk_";
    private static final int GENERATED_KEY_LENGTH = 32;
    private static final TypeToken<Map<String, String>> API_KEYS_TYPE_TOKEN = new TypeToken<>() {};

    private final Path file;
    private final Gson gson;
    private Map<String, String> tokenToLabel = new ConcurrentHashMap<>();

    public ApiKeyAuthenticator(final Path file, final Gson gson) {
        this.file = file;
        this.gson = gson;
    }

    @Override
    public void onInit() {
        this.load();

        final var handler = ServerControl.instance.handler;
        handler.register("nohorny-api-keys", "<add|remove|list> [label]", "Manage API keys", args -> {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add" -> {
                    if (args.length < 2) {
                        logger.error("Usage: nohorny-api-keys add <label>");
                        return;
                    }

                    final var random = new SecureRandom();
                    final var bytes = new byte[GENERATED_KEY_LENGTH];
                    random.nextBytes(bytes);

                    final var label = args[1];
                    final var token = KEY_PREFIX + Base64.getUrlEncoder().encodeToString(bytes);

                    if (this.tokenToLabel.values().stream().anyMatch(label::equalsIgnoreCase)) {
                        logger.error("A token with that label already exists");
                        return;
                    }
                    if (this.tokenToLabel.putIfAbsent(token, label) != null) {
                        logger.error("The token already exists.");
                        return;
                    }

                    System.out.println("Generated API key for '" + label + "': " + token);

                    save();
                }
                case "remove" -> {
                    if (args.length < 2) {
                        logger.error("Usage: nohorny-api-keys remove <label>");
                        return;
                    }
                    if (this.tokenToLabel.values().removeIf(args[1]::equalsIgnoreCase)) {
                        save();
                    } else {
                        logger.error("API key not found");
                    }
                }
                case "list" -> {
                    if (this.tokenToLabel.isEmpty()) {
                        logger.info("No API keys configured");
                    } else {
                        logger.info("Configured API keys ({}):", this.tokenToLabel.size());
                        for (final var entry : this.tokenToLabel.entrySet()) {
                            final var token = entry.getKey();
                            logger.info(
                                    "  - {} ({}...)",
                                    entry.getValue(),
                                    token.substring(0, Math.min(12, token.length())));
                        }
                    }
                }
                default -> logger.info("Unknown action: {}. Use add, remove, or list.", args[0]);
            }
        });
    }

    @Override
    public void onExit() {
        ServerControl.instance.handler.removeCommand("nohorny-api-keys");
    }

    @Override
    public @Nullable Identity identify(final HttpExchange exchange) {
        final var header = exchange.getRequestHeaders().getFirst(AUTHORIZATION_HEADER);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            final var token = header.substring(BEARER_PREFIX.length()).trim();
            final var label = this.tokenToLabel.get(token);
            if (label != null) {
                return new Identity("api-key", label);
            }
        }
        return null;
    }

    private void load() {
        try {
            if (Files.notExists(this.file)) {
                return;
            }
            final var json = Files.readString(this.file, StandardCharsets.UTF_8);
            final var loaded = this.gson.fromJson(json, API_KEYS_TYPE_TOKEN);
            this.tokenToLabel = new ConcurrentHashMap<>(loaded == null ? Map.of() : loaded);
            logger.info("Loaded {} API keys from storage", this.tokenToLabel.size());
        } catch (final Exception e) {
            logger.error("Failed to load API keys from storage", e);
        }
    }

    private void save() {
        try {
            Files.writeString(this.file, this.gson.toJson(this.tokenToLabel), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            logger.error("Failed to save API keys to storage", e);
        }
    }
}
