// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.util.function.Function;
import java.util.function.Supplier;
import mindustry.net.Administration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConfigUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUtils.class);

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
                        LOGGER.atError()
                                .setMessage("The value '{}' for the '{}' config entry is not valid")
                                .addArgument(string)
                                .addArgument(name)
                                .setCause(e)
                                .log();
                    }
                } else {
                    LOGGER.atError()
                            .setMessage("The value '{}' for the '{}' config entry is not a string")
                            .addArgument(value)
                            .addArgument(name)
                            .setCause(new Throwable())
                            .log();
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
