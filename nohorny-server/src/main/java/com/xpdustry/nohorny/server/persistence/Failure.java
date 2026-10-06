// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import java.io.PrintWriter;
import java.io.StringWriter;

/// A classification that threw.
///
/// @param error the stack trace of the exception, its first line starts with the name of the exception class. The
/// failures recorded before only hold that name
public record Failure(String error) implements Outcome {

    public Failure(final Throwable throwable) {
        this(stackTrace(throwable));
    }

    @Override
    public RatingBucket bucket() {
        return RatingBucket.FAILED;
    }

    /// @return the name of the exception class, safe to show publicly unlike the message and the frames
    public String type() {
        final var line = this.error.lines().findFirst().orElse("");
        final var end = line.indexOf(':');
        return (end == -1 ? line : line.substring(0, end)).strip();
    }

    private static String stackTrace(final Throwable throwable) {
        final var writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString().stripTrailing();
    }
}
