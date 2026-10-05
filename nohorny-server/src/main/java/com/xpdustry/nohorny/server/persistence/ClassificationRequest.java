// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.jspecify.annotations.Nullable;

/// A recorded classification request.
///
/// The image itself lives in the [ImageStore], referenced by its hash.
///
/// The reads run outside of transactions, so the steps are fetched eagerly: one query loads those of a whole page.
@Entity
@Table(name = "request")
public class ClassificationRequest extends AssignedIdEntity<String> {

    @Id
    private String id;

    @Convert(converter = EpochMillisConverter.class)
    private Instant createdAt;

    private long durationMillis;

    private String classifier;

    @Column(name = "outcome")
    @Enumerated(EnumType.STRING)
    private RatingBucket bucket;

    private @Nullable Double confidence;

    private @Nullable String error;

    private @Nullable String version;

    private @Nullable String username;

    private String remoteAddress;

    private @Nullable String network;

    private @Nullable String imageMediaType;

    @Enumerated(EnumType.STRING)
    private ImageState imageState;

    private @Nullable String imageHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 128)
    @CollectionTable(name = "request_step", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "position")
    private List<ClassificationStep> steps;

    @SuppressWarnings("NullAway")
    protected ClassificationRequest() {}

    /// @param id the UUIDv7 identifier
    /// @param classifier the classifier of the final verdict, or the one that failed
    /// @param outcome the final verdict, or the failure
    /// @param version the plugin version of the caller, if sent
    /// @param username the authenticated caller
    /// @param network the normalized network name of the listed Mindustry server that sent the request
    /// @param imageMediaType the media type of the image, if it is stored
    /// @param imageHash the [ImageStore#hash] of the image, given if and only if the image state is
    ///     [ImageState#STORED]
    public ClassificationRequest(
            final String id,
            final Instant createdAt,
            final long durationMillis,
            final String classifier,
            final Outcome outcome,
            final @Nullable String version,
            final @Nullable String username,
            final String remoteAddress,
            final @Nullable String network,
            final @Nullable String imageMediaType,
            final ImageState imageState,
            final @Nullable String imageHash,
            final List<ClassificationStep> steps) {
        if ((imageState == ImageState.STORED) != (imageHash != null)) {
            throw new IllegalArgumentException("The image hash must be given if and only if the image is stored");
        }
        this.id = id;
        this.createdAt = createdAt;
        this.durationMillis = durationMillis;
        this.classifier = classifier;
        this.bucket = outcome.bucket();
        this.confidence = Outcome.confidence(outcome);
        this.error = Outcome.error(outcome);
        this.version = version;
        this.username = username;
        this.remoteAddress = remoteAddress;
        this.network = network;
        this.imageMediaType = imageMediaType;
        this.imageState = imageState;
        this.imageHash = imageHash;
        this.steps = new ArrayList<>(steps);
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

    public String getClassifier() {
        return this.classifier;
    }

    public Outcome getOutcome() {
        return Outcome.of(this.bucket, this.confidence, this.error);
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

    public @Nullable String getNetwork() {
        return this.network;
    }

    public @Nullable String getImageMediaType() {
        return this.imageMediaType;
    }

    public ImageState getImageState() {
        return this.imageState;
    }

    public @Nullable String getImageHash() {
        return this.imageHash;
    }

    public List<ClassificationStep> getSteps() {
        return List.copyOf(this.steps);
    }

    public RatingBucket bucket() {
        return this.bucket;
    }
}
