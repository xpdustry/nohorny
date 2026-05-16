// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.util.Log;
import arc.util.Strings;
import java.util.Arrays;
import java.util.Locale;
import mindustry.Vars;

sealed interface MiniLogger {

    static MiniLogger forClass(final Class<?> clazz) {
        final var fallback = MindustryMiniLogger.INSTANCE;
        if (Vars.mods.getMod("slf4md") != null) {
            try {
                return new SLF4MDMiniLogger(clazz);
            } catch (final ReflectiveOperationException e) {
                fallback.error("Failed to create slf4md logger", e);
            }
        }
        return fallback;
    }

    enum Level {
        DEBUG,
        INFO,
        ERROR
    }

    default void debug(final String message, final Object... args) {
        this.log(Level.DEBUG, message, args);
    }

    default void info(final String message, final Object... args) {
        this.log(Level.INFO, message, args);
    }

    default void error(final String message, final Object... args) {
        this.log(Level.ERROR, message, args);
    }

    void log(final Level level, final String message, final Object... args);

    final class SLF4MDMiniLogger implements MiniLogger {
        private final Object logger;
        private boolean isFailureReported = false;

        private SLF4MDMiniLogger(final Class<?> clazz) throws ReflectiveOperationException {
            final var factory = Class.forName("org.slf4j.LoggerFactory");
            final var getLogger = factory.getMethod("getLogger", Class.class);
            this.logger = getLogger.invoke(null, clazz);
        }

        @Override
        public void log(final Level level, final String message, final Object... args) {
            try {
                final var log = Class.forName("org.slf4j.Logger")
                        .getMethod(level.name().toLowerCase(Locale.ROOT), String.class, Object[].class);
                log.invoke(this.logger, message, args);
            } catch (final ReflectiveOperationException e) {
                final var fallback = MindustryMiniLogger.INSTANCE;
                if (!this.isFailureReported) {
                    fallback.error("Failed to log message using slf4md", e);
                    this.isFailureReported = true;
                }
                fallback.log(level, message, args);
            }
        }
    }

    enum MindustryMiniLogger implements MiniLogger {
        INSTANCE;

        private static final String PREFIX = "[NoHorny] ";

        @Override
        public void log(final Level level, final String message, Object... args) {
            final var arcLevel =
                    switch (level) {
                        case DEBUG -> Log.LogLevel.debug;
                        case INFO -> Log.LogLevel.info;
                        case ERROR -> Log.LogLevel.err;
                    };
            Throwable error = null;
            if (args.length != 0 && args[args.length - 1] instanceof Throwable throwable) {
                args = Arrays.copyOf(args, args.length - 1);
                error = throwable;
            }
            Log.log(arcLevel, PREFIX + message.replace("{}", "@"), args);
            if (error != null) {
                Log.log(arcLevel, PREFIX + Strings.getStackTrace(error));
            }
        }
    }
}
