// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.persistence;

import com.xpdustry.nohorny.common.Rating;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "classification_requests")
public class ClassificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    private Instant createdAt;

    private long durationMillis;

    private String classifier;

    @Enumerated(EnumType.STRING)
    private @Nullable Rating rating;

    private @Nullable Double confidence;

    private boolean successful;

    private @Nullable String error;

    private @Nullable String username;

    private String remoteAddress;

    private String imageMediaType;

    private byte[] image;

    protected ClassificationRequest() {}

    public ClassificationRequest(
            final Instant createdAt,
            final long durationMillis,
            final String classifier,
            final @Nullable Rating rating,
            final @Nullable Double confidence,
            final boolean successful,
            final @Nullable String error,
            final @Nullable String username,
            final String remoteAddress,
            final String imageMediaType,
            final byte[] image) {
        this.createdAt = createdAt;
        this.durationMillis = durationMillis;
        this.classifier = classifier;
        this.rating = rating;
        this.confidence = confidence;
        this.successful = successful;
        this.error = error;
        this.username = username;
        this.remoteAddress = remoteAddress;
        this.imageMediaType = imageMediaType;
        this.image = image;
    }

    public long getId() {
        return Objects.requireNonNull(this.id, "The request has not been saved yet");
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public long getDurationMillis() {
        return this.durationMillis;
    }

    public String getClassifier() {
        return this.classifier;
    }

    public @Nullable Rating getRating() {
        return this.rating;
    }

    public @Nullable Double getConfidence() {
        return this.confidence;
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public @Nullable String getError() {
        return this.error;
    }

    public @Nullable String getUsername() {
        return this.username;
    }

    public String getRemoteAddress() {
        return this.remoteAddress;
    }

    public String getImageMediaType() {
        return this.imageMediaType;
    }

    public byte[] getImage() {
        return this.image;
    }
}
