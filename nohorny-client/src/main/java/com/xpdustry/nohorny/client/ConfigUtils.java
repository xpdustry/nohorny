// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.util.function.Function;
import java.util.function.Supplier;
import mindustry.net.Administration;

final class ConfigUtils {

    private static final MiniLogger log = MiniLogger.forClass(ConfigUtils.class);

    public static <T> Supplier<T> registerSafeSettingEntry(
            final String name, final String desc, final T def, final Function<String, T> parser) {
        final var entry = new Administration.Config(name, desc, def.toString()) {
            @Override
            public void set(final Object value) {
                if (value instanceof String string) {
                    try {
                        final var _ = parser.apply(string);
                        super.set(string);
                    } catch (final Exception e) {
                        log.error("The value '{}' for the '{}' config entry is not valid", string, this.name, e);
                    }
                } else {
                    log.error(
                            "The value '{}' for the '{}' config entry is not a string",
                            value,
                            this.name,
                            new IllegalArgumentException());
                }
            }
        };
        return () -> {
            if (!entry.isString()) {
                return def;
            }
            try {
                return parser.apply(entry.string());
            } catch (final Exception _) {
                return def;
            }
        };
    }
}
