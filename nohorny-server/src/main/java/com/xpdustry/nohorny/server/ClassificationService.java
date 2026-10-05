// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.server.classifier.Classifier;
import com.xpdustry.nohorny.server.classifier.ClassifierChain;
import com.xpdustry.nohorny.server.persistence.ClassificationRequest;
import com.xpdustry.nohorny.server.persistence.ClassificationStep;
import com.xpdustry.nohorny.server.persistence.ImageState;
import com.xpdustry.nohorny.server.persistence.UuidV7;
import com.xpdustry.nohorny.server.request.RequestService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
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
        Classifier.@Nullable Result verdict = null;
        @Nullable String verdictClassifier = null;
        @Nullable String error = null;
        for (final var classifier : this.classifiers.classifiers()) {
            final var stepStart = System.nanoTime();
            try {
                final var result = classifier.classify(image);
                log.trace("Processed image {} with {}, got {}", id, classifier.name(), result);
                steps.add(new ClassificationStep(
                        classifier.name(), result.rating(), result.confidence(), millisSince(stepStart), null));
                verdict = result;
                verdictClassifier = classifier.name();
                if (result.rating() != Rating.NSFW) {
                    break;
                }
            } catch (final Exception exception) {
                log.error("Classification request {} failed with {}", id, classifier.name(), exception);
                final var name = exception.getClass().getName();
                steps.add(new ClassificationStep(classifier.name(), null, null, millisSince(stepStart), name));
                // A failure later in the chain falls back to the previous result
                if (verdict == null) {
                    error = name;
                    verdictClassifier = classifier.name();
                }
                break;
            }
        }

        final var rating = verdict == null ? null : verdict.rating();
        final var store = rating != Rating.SAFE;
        final var request = new ClassificationRequest(
                id,
                createdAt,
                millisSince(start),
                verdict != null,
                rating,
                verdict == null ? null : verdict.confidence(),
                // The chain is never empty
                verdictClassifier == null ? "unknown" : verdictClassifier,
                error,
                truncate(submission.version()),
                submission.username(),
                submission.remoteAddress(),
                // Recorded now, the server list changes over time
                this.clients.whois(submission.remoteAddress()).network(),
                store ? submission.mediaType() : null,
                store ? ImageState.STORED : ImageState.NONE,
                steps,
                store ? bytes : null);

        final var recorded = this.record(request);

        if (verdict == null) {
            return ResponseEntity.internalServerError().body(new SimpleServerMessage("internal server error"));
        }
        return ResponseEntity.ok(new ClassificationResponse(
                request.getClassifier(), verdict.rating(), verdict.confidence(), id, recorded ? this.urlOf(id) : null));
    }

    private boolean record(final ClassificationRequest request) {
        try {
            this.requests.record(request);
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

    /// @param mediaType the media type of the image, without parameters
    /// @param version the plugin version of the caller
    /// @param username the authenticated caller
    public record Submission(
            String mediaType,
            @Nullable String version,
            @Nullable String username,
            String remoteAddress) {}
}
