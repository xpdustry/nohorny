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

    void debug(final String message, final Object... args);

    void trace(final String message, final Object... args);

    void info(final String message, final Object... args);

    void warn(final String message, final Object... args);

    void error(final String message, final Object... args);

    final class SLF4MDMiniLogger implements MiniLogger {

        private enum Level {
            TRACE,
            DEBUG,
            INFO,
            WARN,
            ERROR
        }

        private final Object logger;
        private boolean isFailureReported = false;

        private SLF4MDMiniLogger(final Class<?> clazz) throws ReflectiveOperationException {
            final var factory = Class.forName("org.slf4j.LoggerFactory");
            final var getLogger = factory.getMethod("getLogger", Class.class);
            this.logger = getLogger.invoke(null, clazz);
        }

        @Override
        public void debug(final String message, final Object... args) {
            this.log0(Level.DEBUG, message, args);
        }

        @Override
        public void trace(final String message, final Object... args) {
            this.log0(Level.TRACE, message, args);
        }

        @Override
        public void info(final String message, final Object... args) {
            this.log0(Level.INFO, message, args);
        }

        @Override
        public void warn(final String message, final Object... args) {
            this.log0(Level.WARN, message, args);
        }

        @Override
        public void error(final String message, final Object... args) {
            this.log0(Level.ERROR, message, args);
        }

        private void log0(final Level level, final String message, final Object... args) {
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
                switch (level) {
                    case TRACE -> fallback.trace(message, args);
                    case DEBUG -> fallback.debug(message, args);
                    case INFO -> fallback.info(message, args);
                    case WARN -> fallback.warn(message, args);
                    case ERROR -> fallback.error(message, args);
                }
            }
        }
    }

    enum MindustryMiniLogger implements MiniLogger {
        INSTANCE;

        private final String prefix = "[" + Vars.mods.getMod(NoHornyPlugin.class).meta.displayName + "] ";

        @Override
        public void debug(final String message, final Object... args) {
            this.log0(Log.LogLevel.debug, message, args);
        }

        @Override
        public void trace(final String message, final Object... args) {
            this.log0(Log.LogLevel.debug, message, args);
        }

        @Override
        public void info(final String message, final Object... args) {
            this.log0(Log.LogLevel.info, message, args);
        }

        @Override
        public void warn(final String message, final Object... args) {
            this.log0(Log.LogLevel.warn, message, args);
        }

        @Override
        public void error(final String message, final Object... args) {
            this.log0(Log.LogLevel.err, message, args);
        }

        private void log0(final Log.LogLevel level, final String message, Object... args) {
            Throwable error = null;
            if (args.length != 0 && args[args.length - 1] instanceof Throwable throwable) {
                args = Arrays.copyOf(args, args.length - 1);
                error = throwable;
            }
            Log.log(level, this.prefix + message.replace("{}", "@"), args);
            if (error != null) {
                Log.log(level, this.prefix + Strings.getStackTrace(error));
            }
        }
    }
}
