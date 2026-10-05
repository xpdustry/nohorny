// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Events;
import arc.util.Strings;
import java.util.function.Consumer;
import mindustry.net.Administration;
import org.jspecify.annotations.Nullable;

final class AdminConfigNoHornySetting<T> implements NoHornySetting<T> {

    private static final MiniLogger log = MiniLogger.forClass(AdminConfigNoHornySetting.class);

    private final String name;
    private final String description;
    private final @Nullable T def;
    private final Class<T> type;
    private final SettingCodec<T> codec;
    private final StrictStringAdminConfig config;

    AdminConfigNoHornySetting(
            final String name,
            final String description,
            final @Nullable T def,
            final Class<T> type,
            final SettingCodec<T> codec) {
        this.name = name;
        this.description = description;
        this.def = def;
        this.type = type;
        this.codec = codec;
        this.config = new StrictStringAdminConfig(
                name,
                description,
                def == null ? "null" : codec.encode(def),
                value -> {
                    if (!"null".equalsIgnoreCase(value)) {
                        this.codec.decode(value);
                    }
                },
                () -> Events.fire(new SettingChangeEvent(this)));
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public @Nullable T def() {
        return this.def;
    }

    @Override
    public Class<T> type() {
        return this.type;
    }

    @Override
    public @Nullable T get() {
        final var value = this.config.get();
        if ("null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return this.codec.decode(value);
        } catch (final Exception _) {
            return this.def;
        }
    }

    @Override
    public void set(final @Nullable T value) {
        try {
            this.config.set(value == null ? "null" : this.codec.encode(value));
        } catch (final Exception e) {
            log.error("Failed to encode value {} for nohorny config", value, this.name);
        }
    }

    private static final class StrictStringAdminConfig extends Administration.Config {

        private final Consumer<String> validator;

        private StrictStringAdminConfig(
                final String name,
                final String description,
                final String def,
                Consumer<String> validator,
                final Runnable onChange) {
            super("nohorny-" + name, description.replace("\n", " "), def, onChange);
            this.validator = validator;
        }

        @Override
        public void set(@Nullable Object value) {
            if (value == null) {
                value = "null";
            }
            if (value instanceof String string) {
                if ("null".equalsIgnoreCase(string)) {
                    string = "null";
                }
                if (string.equals(this.get())) {
                    return;
                }
                try {
                    this.validator.accept(string);
                    super.set(string);
                } catch (final Exception e) {
                    log.error(
                            "The value '{}' for the '{}' config entry is not valid: {}",
                            string,
                            this.name,
                            Strings.getSimpleMessage(e));
                }
            } else {
                log.error(
                        "The value '{}' for the '{}' config entry is not a string",
                        value,
                        this.name,
                        new IllegalArgumentException());
            }
        }

        @Override
        public String get() {
            if (super.get() instanceof String string) {
                return string;
            } else {
                return (String) this.defaultValue;
            }
        }
    }
}
