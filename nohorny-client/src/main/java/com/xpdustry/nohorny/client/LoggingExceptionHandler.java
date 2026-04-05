// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import org.slf4j.LoggerFactory;

enum LoggingExceptionHandler implements Thread.UncaughtExceptionHandler {
    INSTANCE;

    @Override
    public void uncaughtException(final Thread t, final Throwable e) {
        LoggerFactory.getLogger(NoHornyPlugin.class).error("An error occurred in thread {}", t.getName(), e);
    }
}
