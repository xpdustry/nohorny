// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.persistence.ClassificationRequest;
import com.xpdustry.nohorny.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.persistence.RequestProperties;
import com.xpdustry.nohorny.server.classifier.Classifier;
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

    private final StatusProperties status;
    private final Classifier classifier;
    private final ClassificationRequestRepository requests;
    private final RequestProperties requestProperties;

    public NoHornyController(
            final StatusProperties status,
            final Classifier classifier,
            final ClassificationRequestRepository requests,
            final RequestProperties requestProperties) {
        this.status = status;
        this.classifier = classifier;
        this.requests = requests;
        this.requestProperties = requestProperties;
    }

    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleServerMessage onStatus() {
        return new SimpleServerMessage(this.status.motd());
    }

    @PostMapping(
            path = "/classify",
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
        final var startedAt = Instant.now();
        final var startedNanos = System.nanoTime();
        final var mediaType = MediaType.parseMediaType(request.getContentType());
        final var imageMediaType = mediaType.getType() + "/" + mediaType.getSubtype();
        final Classifier.Result result;
        try {
            result = this.classifier.classify(image);
        } catch (final Exception exception) {
            final var id = this.saveRequest(new ClassificationRequest(
                    startedAt,
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                    this.classifier.name(),
                    null,
                    null,
                    false,
                    exception.getClass().getSimpleName(),
                    principal == null ? null : principal.getName(),
                    request.getRemoteAddr(),
                    imageMediaType,
                    data));
            log.error("Classification request {} has failed", id, exception);
            return ResponseEntity.internalServerError().body(new SimpleServerMessage("internal server error"));
        }
        final var id = this.saveRequest(new ClassificationRequest(
                startedAt,
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                this.classifier.name(),
                result.rating(),
                result.confidence(),
                true,
                null,
                principal == null ? null : principal.getName(),
                request.getRemoteAddr(),
                imageMediaType,
                data));
        return ResponseEntity.ok(new ClassificationResponse(
                this.classifier.name(), result.rating(), result.confidence(), Long.toString(id)));
    }

    private long saveRequest(final ClassificationRequest request) {
        final var id = this.requests.save(request).getId();
        this.requests.deleteOverCapacity(this.requestProperties.capacity());
        return id;
    }
}
