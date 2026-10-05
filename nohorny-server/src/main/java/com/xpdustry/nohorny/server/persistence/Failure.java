// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

/// A classification that threw.
///
/// @param error the name of the exception class
public record Failure(String error) implements Outcome {

    public Failure(final Throwable throwable) {
        this(throwable.getClass().getName());
    }

    @Override
    public RatingBucket bucket() {
        return RatingBucket.FAILED;
    }
}
