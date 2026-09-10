// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.server.classifier.ClassifierChain;
import java.awt.image.BufferedImage;
import java.util.UUID;
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
    private final ClassifierChain classifiers;

    public NoHornyController(final StatusProperties status, final ClassifierChain classifiers) {
        this.status = status;
        this.classifiers = classifiers;
    }

    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleServerMessage onStatus() {
        return new SimpleServerMessage(this.status.motd());
    }

    @PostMapping(
            path = "/classify",
            consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> onClassify(final @RequestBody BufferedImage image) {
        return this.classify(image);
    }

    private ResponseEntity<?> classify(final BufferedImage image) {
        final var uuid = UUID.randomUUID().toString();
        ResponseEntity<?> response =
                ResponseEntity.internalServerError().body(new SimpleServerMessage("internal server error"));
        log.trace("Processing image {} (w={},h={})", uuid, image.getWidth(), image.getHeight());
        for (final var classifier : this.classifiers.classifiers()) {
            try {
                final var result = classifier.classify(image);
                log.trace("Processed image {} with {}, got {}", uuid, classifier.name(), result);
                response = ResponseEntity.ok(
                        new ClassificationResponse(classifier.name(), result.rating(), result.confidence(), uuid));
                if (result.rating() != Rating.NSFW) {
                    break;
                }
            } catch (final Exception exception) {
                log.error("Classification request {} failed with {}", uuid, classifier.name(), exception);
                break;
            }
        }
        return response;
    }
}
