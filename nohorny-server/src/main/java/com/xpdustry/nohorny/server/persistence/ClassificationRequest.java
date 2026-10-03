// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import com.xpdustry.nohorny.common.Rating;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// A recorded classification request.
///
/// Only written through this entity, the reads go through [ClassificationRequestSummary] and [StoredImage] so the
/// image bytes are never loaded unless requested.
@Entity
@Table(name = "request")
public class ClassificationRequest extends AssignedIdEntity<String> {

    @Id
    private String id;

    @Convert(converter = EpochMillisConverter.class)
    private Instant createdAt;

    private long durationMillis;

    private boolean successful;

    @Enumerated(EnumType.STRING)
    private @Nullable Rating rating;

    private @Nullable Double confidence;

    private String classifier;

    private @Nullable String error;

    private @Nullable String version;

    private @Nullable String username;

    private String remoteAddress;

    private @Nullable String imageMediaType;

    @Enumerated(EnumType.STRING)
    private ImageState imageState;

    @ElementCollection
    @CollectionTable(name = "request_step", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "position")
    private List<ClassificationStep> steps;

    // Only read through StoredImage, loading it with the entity would defeat the projections.
    //   Not a @Lob, Hibernate would read it with ResultSet#getBlob which SQLite JDBC does not implement.
    @SuppressWarnings("UnusedVariable")
    private byte @Nullable [] image;

    @SuppressWarnings("NullAway")
    protected ClassificationRequest() {}

    /// @param id the UUIDv7 identifier
    /// @param rating the final rating, `null` if the request failed
    /// @param confidence the final confidence, `null` if the request failed
    /// @param classifier the classifier of the final verdict, or the one that failed
    /// @param error the exception name if the request failed
    /// @param version the plugin version of the caller, if sent
    /// @param username the authenticated caller
    /// @param imageMediaType the media type of the image, if it is stored
    /// @param image the bytes to store, given if and only if the image state is [ImageState#STORED]
    public ClassificationRequest(
            final String id,
            final Instant createdAt,
            final long durationMillis,
            final boolean successful,
            final @Nullable Rating rating,
            final @Nullable Double confidence,
            final String classifier,
            final @Nullable String error,
            final @Nullable String version,
            final @Nullable String username,
            final String remoteAddress,
            final @Nullable String imageMediaType,
            final ImageState imageState,
            final List<ClassificationStep> steps,
            final byte @Nullable [] image) {
        if ((imageState == ImageState.STORED) != (image != null)) {
            throw new IllegalArgumentException("The image bytes must be given if and only if the image is stored");
        }
        this.id = id;
        this.createdAt = createdAt;
        this.durationMillis = durationMillis;
        this.successful = successful;
        this.rating = rating;
        this.confidence = confidence;
        this.classifier = classifier;
        this.error = error;
        this.version = version;
        this.username = username;
        this.remoteAddress = remoteAddress;
        this.imageMediaType = imageMediaType;
        this.imageState = imageState;
        this.steps = new ArrayList<>(steps);
        this.image = image;
    }

    @Override
    public String getId() {
        return this.id;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public long getDurationMillis() {
        return this.durationMillis;
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public @Nullable Rating getRating() {
        return this.rating;
    }

    public @Nullable Double getConfidence() {
        return this.confidence;
    }

    public String getClassifier() {
        return this.classifier;
    }

    public @Nullable String getError() {
        return this.error;
    }

    public @Nullable String getVersion() {
        return this.version;
    }

    public @Nullable String getUsername() {
        return this.username;
    }

    public String getRemoteAddress() {
        return this.remoteAddress;
    }

    public @Nullable String getImageMediaType() {
        return this.imageMediaType;
    }

    public ImageState getImageState() {
        return this.imageState;
    }

    public List<ClassificationStep> getSteps() {
        return List.copyOf(this.steps);
    }

    public RatingBucket bucket() {
        return RatingBucket.of(this.rating);
    }
}
