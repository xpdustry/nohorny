// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.ClassificationResponse;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.MindustryImageIO;
import com.xpdustry.nohorny.common.MindustryImageRenderer;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.awt.image.BufferedImage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class NoHornyController {

    private static final Logger log = LoggerFactory.getLogger(NoHornyController.class);

    private final Classifier classifier;

    @Autowired
    public NoHornyController(final Classifier classifier) {
        this.classifier = classifier;
    }

    @GetMapping(path = "/status", produces = MediaType.TEXT_PLAIN_VALUE)
    public String onStatus() {
        return "ok";
    }

    @PostMapping(
            path = "/classify",
            consumes = MindustryImageIO.MEDIA_TYPE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> onClassify(final @RequestBody VirtualBuilding.Group<? extends MindustryImage> group) {
        return this.classify(MindustryImageRenderer.render(group));
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
        try {
            log.trace("Processing image {} (png/w={},h={})", uuid, image.getWidth(), image.getHeight());
            final var result = this.classifier.classify(image);
            log.trace("Processed image {}, got {}", uuid, result);
            return ResponseEntity.ok(new ClassificationResponse(this.classifier.name(), result.rating(), uuid));
        } catch (final Exception exception) {
            log.error("Classification request {} has failed", uuid, exception);
            return ResponseEntity.internalServerError().body("internal server error");
        }
    }
}
