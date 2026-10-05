// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.jspecify.annotations.Nullable;

/// One classifier invocation of a [ClassificationRequest].
@Embeddable
public class ClassificationStep {

    private String classifier;

    private long durationMillis;

    @Column(name = "outcome")
    @Enumerated(EnumType.STRING)
    private RatingBucket bucket;

    private @Nullable Double confidence;

    private @Nullable String error;

    @SuppressWarnings("NullAway")
    protected ClassificationStep() {}

    public ClassificationStep(final String classifier, final long durationMillis, final Outcome outcome) {
        this.classifier = classifier;
        this.durationMillis = durationMillis;
        this.bucket = outcome.bucket();
        this.confidence = Outcome.confidence(outcome);
        this.error = Outcome.error(outcome);
    }

    public String classifier() {
        return this.classifier;
    }

    public long durationMillis() {
        return this.durationMillis;
    }

    public Outcome outcome() {
        return Outcome.of(this.bucket, this.confidence, this.error);
    }
}
