// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public interface NoHornySetting<T> {

    NoHornySetting<URI> API_ENDPOINT = new AdminConfigNoHornySetting<>(
            "api-endpoint", """
            The NoHorny server endpoint to query for image classification.
            Setting this to null disables classification.
            eg: "http://localhost:8080", "https://nohorny.xpdustry.com/api".
            """, URI.create("https://nohorny.xpdustry.com/api"), URI.class, SettingCodec.OfURI);

    NoHornySetting<NoHornyClientAuthType> API_AUTH_TYPE = new AdminConfigNoHornySetting<>(
            "api-auth-type",
            """
            The auth type to use against the NoHorny server.
            eg: "disabled", "basic", "bearer".
            """,
            NoHornyClientAuthType.DISABLED,
            NoHornyClientAuthType.class,
            new SettingCodec.OfEnum<>(NoHornyClientAuthType.class));

    NoHornySetting<String> API_AUTH_VALUE =
            new AdminConfigNoHornySetting<>("api-auth-value", """
            The auth value to use against the NoHorny API.
            eg: "my-username:my-password" for basic, "my-token" for bearer.
            """, null, String.class, SettingCodec.OfString);

    NoHornySetting<URI> DISCORD_WEBHOOK =
            new AdminConfigNoHornySetting<>("discord-webhook", """
            Send warnings to a discord webhook when unsafe buildings are detected.
            eg: "https://discord.com/api/webhooks/999999/abcdefgh".
            """, null, URI.class, SettingCodec.OfURI);

    NoHornySetting<AutoModeratorPolicy> AUTOMOD_POLICY = new AdminConfigNoHornySetting<>(
            "automod-policy",
            "Then policy to adopt when a group of buildings is classified."
                    + Arrays.stream(AutoModeratorPolicy.values())
                            .map(p -> "\"" + p.name().toLowerCase(Locale.ROOT) + "\"")
                            .sorted()
                            .collect(Collectors.joining(", ", "\neg: ", ".")),
            AutoModeratorPolicy.BAN_NSFW,
            AutoModeratorPolicy.class,
            new SettingCodec.OfEnum<>(AutoModeratorPolicy.class));

    NoHornySetting<Boolean> DEBUG_TAP =
            new AdminConfigNoHornySetting<>("debug-tap", """
            Toggle nohorny debug tap for admins.
            If you double tap on a group of logic displays or canvases,
            it will show you how it is tracked by nohorny and
            also create a file of the rendering result of said group.
            eg: "true", "false".
            """, false, Boolean.class, SettingCodec.OfBoolean);

    List<NoHornySetting<?>> ALL =
            List.of(API_ENDPOINT, API_AUTH_TYPE, API_AUTH_VALUE, DISCORD_WEBHOOK, AUTOMOD_POLICY, DEBUG_TAP);

    String name();

    String description();

    @Nullable T def();

    Class<T> type();

    @Nullable T get();

    void set(final @Nullable T value);
}
