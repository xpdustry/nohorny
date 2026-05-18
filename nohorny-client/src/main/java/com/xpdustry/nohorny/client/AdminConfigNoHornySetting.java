// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Events;
import arc.util.Strings;
import mindustry.net.Administration;
import org.jspecify.annotations.Nullable;

final class AdminConfigNoHornySetting<T> implements NoHornySetting<T> {

    private static final MiniLogger log = MiniLogger.forClass(AdminConfigNoHornySetting.class);

    private final String name;
    private final @Nullable T rawDefaultValue;
    private final Class<T> type;
    private final Administration.Config config;

    AdminConfigNoHornySetting(
            final String name,
            final String description,
            final @Nullable T def,
            final Class<T> type,
            final SettingCodec<T> codec) {
        this.name = name;
        this.rawDefaultValue = def;
        this.type = type;
        this.config =
                new Administration.Config(
                        "nohorny-" + name,
                        description.replace("\n", " "),
                        def == null ? "" : codec.encode(def),
                        () -> Events.fire(new SettingChangeEvent(AdminConfigNoHornySetting.this))) {
                    @Override
                    public void set(final @Nullable Object value) {
                        if (value instanceof String string) {
                            if ("null".equalsIgnoreCase(string)) {
                                super.set("null");
                                return;
                            }
                            try {
                                codec.decode(string);
                                super.set(string);
                            } catch (final Exception e) {
                                log.error(
                                        "The value '{}' for the '{}' config entry is not valid: {}",
                                        string,
                                        this.name,
                                        Strings.getSimpleMessage(e));
                            }
                        } else if (value == null) {
                            super.set("null");
                        } else if (type.isInstance(value)) {
                            try {
                                super.set(codec.encode(type.cast(value)));
                            } catch (final Exception e) {
                                log.error(
                                        "Failed to serialize value '{}' for the '{}' config entry",
                                        value,
                                        this.name,
                                        e);
                            }
                        } else {
                            log.error(
                                    "The value '{}' for the '{}' config entry is not a string nor an instance of {}",
                                    value,
                                    this.name,
                                    AdminConfigNoHornySetting.this.type().getSimpleName(),
                                    new IllegalArgumentException());
                        }
                    }

                    @Override
                    public @Nullable T get() {
                        final var value = super.get();
                        if (value instanceof String string) {
                            if ("null".equalsIgnoreCase(string)) {
                                return null;
                            }
                            try {
                                return codec.decode(string);
                            } catch (final Exception ignored) {
                                log.debug("Failed to decode value '{}' for the '{}' config entry", string, this.name);
                            }
                        }
                        return AdminConfigNoHornySetting.this.rawDefaultValue;
                    }
                };
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String description() {
        return this.config.description;
    }

    @Override
    public @Nullable T def() {
        return this.rawDefaultValue;
    }

    @Override
    public Class<T> type() {
        return this.type;
    }

    @Override
    public @Nullable T get() {
        final var value = this.config.get();
        return value == null ? null : this.type.cast(value);
    }

    @Override
    public void set(final @Nullable T value) {
        this.config.set(value);
    }
}
