// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.server.classifier.ClassifierChain;
import com.xpdustry.nohorny.server.persistence.ClassificationRequest;
import com.xpdustry.nohorny.server.persistence.ClassificationStep;
import com.xpdustry.nohorny.server.persistence.Failure;
import com.xpdustry.nohorny.server.persistence.ImageState;
import com.xpdustry.nohorny.server.persistence.ImageStore;
import com.xpdustry.nohorny.server.persistence.Outcome;
import com.xpdustry.nohorny.server.persistence.RatingBucket;
import com.xpdustry.nohorny.server.persistence.UuidV7;
import com.xpdustry.nohorny.server.persistence.Verdict;
import com.xpdustry.nohorny.server.request.RequestService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/// Runs the classifier chain on submitted images and records every classification.
@Service
public final class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);
    private static final int MAX_VERSION_LENGTH = 64;

    private final ClassifierChain classifiers;
    private final RequestService requests;
    private final NoHornyProperties properties;
    private final ApplicationEventPublisher events;
    private final MindustryClientDirectory clients;

    public ClassificationService(
            final ClassifierChain classifiers,
            final RequestService requests,
            final NoHornyProperties properties,
            final ApplicationEventPublisher events,
            final MindustryClientDirectory clients) {
        this.classifiers = classifiers;
        this.requests = requests;
        this.properties = properties;
        this.events = events;
        this.clients = clients;
    }

    /// @param bytes the image as received
    public ResponseEntity<?> classify(final byte[] bytes, final Submission submission) {
        final var createdAt = Instant.now();
        final var start = System.nanoTime();
        final BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (final IOException exception) {
            return invalidImage();
        }
        if (image == null) {
            return invalidImage();
        }

        final var id = UuidV7.next().toString();
        log.trace("Processing image {} (w={},h={})", id, image.getWidth(), image.getHeight());

        final var steps = new ArrayList<ClassificationStep>();
        final var outcome = this.run(id, image, steps);
        // Safe images are never kept
        final var store = outcome.outcome().bucket() != RatingBucket.SAFE;
        final var request = new ClassificationRequest(
                id,
                createdAt,
                millisSince(start),
                outcome.classifier(),
                outcome.outcome(),
                truncate(submission.version()),
                submission.username(),
                submission.remoteAddress(),
                // Recorded now, the server list changes over time
                this.clients.whois(submission.remoteAddress()).network(),
                store ? submission.mediaType() : null,
                store ? ImageState.STORED : ImageState.NONE,
                store ? ImageStore.hash(bytes) : null,
                steps);

        final var recorded = this.record(request, store ? bytes : null);

        return switch (outcome.outcome()) {
            case Verdict verdict ->
                ResponseEntity.ok(new ClassificationResponse(
                        outcome.classifier(),
                        verdict.rating(),
                        verdict.confidence(),
                        id,
                        recorded ? this.urlOf(id) : null));
            case Failure ignored ->
                ResponseEntity.internalServerError().body(new SimpleServerMessage("internal server error"));
        };
    }

    /// Runs the chain, which advances while the verdicts are NSFW.
    ///
    /// @param steps receives one step per classifier invoked
    /// @return the final outcome and the classifier that produced it
    private Final run(final String id, final BufferedImage image, final List<ClassificationStep> steps) {
        @Nullable Final verdict = null;
        for (final var classifier : this.classifiers.classifiers()) {
            final var start = System.nanoTime();
            try {
                final var result = classifier.classify(image);
                log.trace("Processed image {} with {}, got {}", id, classifier.name(), result);
                steps.add(new ClassificationStep(
                        classifier.name(), millisSince(start), new Verdict(result.rating(), result.confidence())));
                verdict = new Final(classifier.name(), new Verdict(result.rating(), result.confidence()));
                if (result.rating() != Rating.NSFW) {
                    return verdict;
                }
            } catch (final Exception exception) {
                log.error("Classification request {} failed with {}", id, classifier.name(), exception);
                steps.add(new ClassificationStep(classifier.name(), millisSince(start), new Failure(exception)));
                // A failure later in the chain falls back to the previous verdict
                return verdict == null ? new Final(classifier.name(), new Failure(exception)) : verdict;
            }
        }
        // The chain is never empty, so the loop produced a verdict
        return Objects.requireNonNull(verdict);
    }

    private boolean record(final ClassificationRequest request, final byte @Nullable [] image) {
        try {
            this.requests.record(request, image);
        } catch (final RuntimeException exception) {
            log.error("Failed to record classification request {}", request.getId(), exception);
            return false;
        }
        try {
            this.events.publishEvent(new ClassificationRecordedEvent(request.getId()));
        } catch (final RuntimeException exception) {
            log.error("Failed to publish classification request {}", request.getId(), exception);
        }
        return true;
    }

    private @Nullable String urlOf(final String id) {
        final var base = this.properties.publicUrl();
        return base == null
                ? null
                : UriComponentsBuilder.fromUri(base)
                        .pathSegment("requests", id)
                        .build()
                        .toUriString();
    }

    private static ResponseEntity<?> invalidImage() {
        return ResponseEntity.badRequest().body(new SimpleServerMessage("invalid image"));
    }

    private static long millisSince(final long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanos);
    }

    private static @Nullable String truncate(final @Nullable String version) {
        return version == null || version.length() <= MAX_VERSION_LENGTH
                ? version
                : version.substring(0, MAX_VERSION_LENGTH);
    }

    /// The outcome of the chain, see [#run].
    private record Final(String classifier, Outcome outcome) {}

    /// @param mediaType the media type of the image, without parameters
    /// @param version the plugin version of the caller
    /// @param username the authenticated caller
    public record Submission(
            String mediaType,
            @Nullable String version,
            @Nullable String username,
            String remoteAddress) {}
}
