// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.persistence.ClassificationRequest;
import com.xpdustry.nohorny.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.persistence.RequestProperties;
import com.xpdustry.nohorny.server.classifier.Classifier;
import com.xpdustry.nohorny.server.classifier.ClassifierChain;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@EnableConfigurationProperties(StatusProperties.class)
@RestController
public final class NoHornyController {

    private static final Logger log = LoggerFactory.getLogger(NoHornyController.class);
    private static final String VERSION_HEADER = "X-NoHorny-Version";

    private final StatusProperties status;
    private final ClassifierChain classifiers;
    private final ClassificationRequestRepository requests;
    private final RequestProperties requestProperties;

    public NoHornyController(
            final StatusProperties status,
            final ClassifierChain classifiers,
            final ClassificationRequestRepository requests,
            final RequestProperties requestProperties) {
        this.status = status;
        this.classifiers = classifiers;
        this.requests = requests;
        this.requestProperties = requestProperties;
    }

    @GetMapping(path = "/api/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleServerMessage onStatus() {
        return new SimpleServerMessage(this.status.motd());
    }

    @PostMapping(
            path = "/api/classify",
            consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> onClassify(
            final @RequestBody byte[] data, final HttpServletRequest request, final @Nullable Principal principal) {
        final BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(data));
        } catch (final IOException exception) {
            return ResponseEntity.badRequest().body(new SimpleServerMessage("invalid image"));
        }
        if (image == null) {
            return ResponseEntity.badRequest().body(new SimpleServerMessage("invalid image"));
        }
        return this.classify(image, data, request, principal);
    }

    private ResponseEntity<?> classify(
            final BufferedImage image,
            final byte[] data,
            final HttpServletRequest request,
            final @Nullable Principal principal) {
        final var mediaType = MediaType.parseMediaType(request.getContentType());
        final var imageMediaType = mediaType.getType() + "/" + mediaType.getSubtype();
        final var username = principal == null ? null : principal.getName();
        final var chain = this.classifiers.classifiers();
        final var firstClassifier = chain.getFirst();
        final var firstStartedAt = Instant.now();
        final var firstStartedNanos = System.nanoTime();
        final Classifier.Result firstResult;
        try {
            firstResult = firstClassifier.classify(image);
        } catch (final Exception exception) {
            final var id = this.saveRequest(this.newRequest(
                    firstStartedAt,
                    firstStartedNanos,
                    firstClassifier,
                    null,
                    exception,
                    username,
                    request,
                    imageMediaType,
                    data));
            log.error("Classification request {} has failed", id, exception);
            return ResponseEntity.internalServerError().body(new SimpleServerMessage("internal server error"));
        }
        final var firstId = this.saveRequest(this.newRequest(
                firstStartedAt,
                firstStartedNanos,
                firstClassifier,
                firstResult,
                null,
                username,
                request,
                imageMediaType,
                data));
        var classifierResult = new Classification(firstClassifier, firstResult, firstId);

        for (final var classifier : chain.subList(1, chain.size())) {
            if (classifierResult.result().rating() != Rating.NSFW) {
                break;
            }
            final var startedAt = Instant.now();
            final var startedNanos = System.nanoTime();
            final Classifier.Result result;
            try {
                result = classifier.classify(image);
            } catch (final Exception exception) {
                final var id = this.saveRequest(this.newRequest(
                        startedAt, startedNanos, classifier, null, exception, username, request, imageMediaType, data));
                log.error(
                        "Classification chain request {} has failed, falling back to the previous result",
                        id,
                        exception);
                break;
            }
            final var id = this.saveRequest(this.newRequest(
                    startedAt, startedNanos, classifier, result, null, username, request, imageMediaType, data));
            classifierResult = new Classification(classifier, result, id);
        }
        return ResponseEntity.ok(new ClassificationResponse(
                classifierResult.classifier().name(),
                classifierResult.result().rating(),
                classifierResult.result().confidence(),
                Long.toString(classifierResult.identifier())));
    }

    private ClassificationRequest newRequest(
            final Instant startedAt,
            final long startedNanos,
            final Classifier classifier,
            final Classifier.@Nullable Result result,
            final @Nullable Exception error,
            final @Nullable String username,
            final HttpServletRequest request,
            final String imageMediaType,
            final byte[] data) {
        return new ClassificationRequest(
                startedAt,
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                classifier.name(),
                result == null ? null : result.rating(),
                result == null ? null : result.confidence(),
                error == null,
                error == null ? null : error.getClass().getSimpleName(),
                username,
                request.getHeader(VERSION_HEADER),
                request.getRemoteAddr(),
                imageMediaType,
                data);
    }

    private long saveRequest(final ClassificationRequest request) {
        final var id = this.requests.save(request).getId();
        this.requests.deleteOverCapacity(this.requestProperties.capacity());
        return id;
    }

    private record Classification(Classifier classifier, Classifier.Result result, long identifier) {}
}
