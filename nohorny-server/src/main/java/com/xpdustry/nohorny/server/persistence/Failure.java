// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.io.PrintWriter;
import java.io.StringWriter;

public record Failure(String error) implements Outcome {

    public Failure(final Throwable throwable) {
        this(stackTrace(throwable));
    }

    @Override
    public RatingBucket bucket() {
        return RatingBucket.FAILED;
    }

    public String type() {
        final var line = this.error.lines().findFirst().orElse("");
        final var end = line.indexOf(':');
        return (end == -1 ? line : line.substring(0, end)).strip();
    }

    private static String stackTrace(final Throwable throwable) {
        final var writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        final var trace = writer.toString().stripTrailing();
        // The first line comes from toString(), an override could put the message before the class name
        final var name = throwable.getClass().getName();
        final var line = trace.lines().findFirst().orElse("");
        return line.equals(name) || line.startsWith(name + ':') ? trace : name + '\n' + trace;
    }
}
