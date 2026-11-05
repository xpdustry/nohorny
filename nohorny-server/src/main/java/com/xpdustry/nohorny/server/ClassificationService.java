// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.ArtKind;
import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.server.classifier.ClassifierChain;
import com.xpdustry.nohorny.server.persistence.ClassificationRequest;
import com.xpdustry.nohorny.server.persistence.ClassificationStep;
import com.xpdustry.nohorny.server.persistence.Failure;
import com.xpdustry.nohorny.server.persistence.ImageState;
import com.xpdustry.nohorny.server.persistence.ImageStore;
import com.xpdustry.nohorny.server.persistence.Outcome;
import com.xpdustry.nohorny.server.persistence.Requester;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public final class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);
    private static final int MAX_STORED_VERSION_LENGTH = 64;

    private final ClassifierChain classifiers;
    private final RequestService requests;
    private final NoHornyProperties properties;

    public ClassificationService(
            final ClassifierChain classifiers, final RequestService requests, final NoHornyProperties properties) {
        this.classifiers = classifiers;
        this.requests = requests;
        this.properties = properties;
    }

    public ResponseEntity<?> classify(final byte[] bytes, final Submission submission) {
        final var createdAt = Instant.now();
        final var start = System.nanoTime();
        final var image = readJpeg(bytes);
        if (image == null) {
            return invalidImage();
        }

        final var id = UuidV7.next().toString();
        log.trace("Processing image {} (w={},h={})", id, image.getWidth(), image.getHeight());

        final var run = this.run(id, image);
        final var request = new ClassificationRequest(
                id,
                createdAt,
                millisSince(start),
                run.classifier(),
                run.outcome(),
                truncate(submission.version()),
                submission.kind(),
                submission.requester(),
                submission.authenticated(),
                submission.remoteAddress(),
                ImageState.STORED,
                ImageStore.hash(bytes),
                run.steps());

        final var recorded = this.record(request, bytes);

        return switch (run.outcome()) {
            case Verdict verdict ->
                ResponseEntity.ok(new ClassificationResponse(
                        run.classifier(),
                        verdict.rating(),
                        verdict.confidence(),
                        id,
                        recorded ? this.urlOf(id) : null));
            case Failure ignored ->
                ResponseEntity.internalServerError().body(new SimpleServerMessage("internal server error"));
        };
    }

    /// Runs the chain, which advances while the verdicts are NSFW.
    private Run run(final String id, final BufferedImage image) {
        final var steps = new ArrayList<ClassificationStep>();
        @Nullable Run last = null;
        for (final var classifier : this.classifiers.classifiers()) {
            final var start = System.nanoTime();
            try {
                final var result = classifier.classify(image);
                log.trace("Processed image {} with {}, got {}", id, classifier.name(), result);
                final var verdict = new Verdict(result.rating(), result.confidence());
                steps.add(new ClassificationStep(classifier.name(), millisSince(start), verdict));
                last = new Run(classifier.name(), verdict, steps);
                if (result.rating() != Rating.NSFW) {
                    return last;
                }
            } catch (final Exception exception) {
                log.error("Classification request {} failed with {}", id, classifier.name(), exception);
                steps.add(new ClassificationStep(classifier.name(), millisSince(start), new Failure(exception)));
                return last == null ? new Run(classifier.name(), new Failure(exception), steps) : last;
            }
        }
        return Objects.requireNonNull(last);
    }

    private boolean record(final ClassificationRequest request, final byte @Nullable [] image) {
        try {
            this.requests.record(request, image);
        } catch (final RuntimeException exception) {
            log.error("Failed to record classification request {}", request.getId(), exception);
            return false;
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

    /// Decodes with the JPEG reader rather than probing the format, so the images stored are always JPEGs.
    ///
    /// The dimensions are read from the header first, a small but highly compressed image could decode into a huge
    /// one. The plugin never renders larger than [MindustryImage#MAX_RENDER_SIZE].
    ///
    /// @return `null` if the bytes are not a JPEG image, or a too large one
    private static @Nullable BufferedImage readJpeg(final byte[] bytes) {
        final var readers = ImageIO.getImageReadersByFormatName("jpeg");
        if (!readers.hasNext()) {
            throw new IllegalStateException("No JPEG image reader is available");
        }
        final var reader = readers.next();
        try (final var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, true, true);
            if (reader.getWidth(0) > MindustryImage.MAX_RENDER_SIZE
                    || reader.getHeight(0) > MindustryImage.MAX_RENDER_SIZE) {
                return null;
            }
            return reader.read(0);
        } catch (final IOException | RuntimeException exception) {
            return null;
        } finally {
            reader.dispose();
        }
    }

    private static ResponseEntity<?> invalidImage() {
        return ResponseEntity.badRequest().body(new SimpleServerMessage("invalid image"));
    }

    private static long millisSince(final long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanos);
    }

    private static @Nullable String truncate(final @Nullable String version) {
        return version == null || version.length() <= MAX_STORED_VERSION_LENGTH
                ? version
                : version.substring(0, MAX_STORED_VERSION_LENGTH);
    }

    /// @param outcome the final outcome, produced by the `classifier`
    /// @param steps one step per classifier invoked
    private record Run(String classifier, Outcome outcome, List<ClassificationStep> steps) {}

    public record Submission(
            @Nullable String version,
            @Nullable ArtKind kind,
            Requester requester,
            boolean authenticated,
            String remoteAddress) {}
}
