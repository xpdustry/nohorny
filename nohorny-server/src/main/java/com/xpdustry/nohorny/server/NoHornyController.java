// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.classification.ClassificationResponse;
import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.image.ImageBinaryCodec;
import com.xpdustry.nohorny.common.image.ImageRenderer;
import com.xpdustry.nohorny.common.image.MindustryImage;
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

    private static final Logger logger = LoggerFactory.getLogger(NoHornyController.class);

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
            consumes = ImageBinaryCodec.MEDIA_TYPE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> onClassify(final @RequestBody VirtualBuilding.Group<? extends MindustryImage> group) {
        try {
            final var result = this.classifier.classify(ImageRenderer.render(group));
            return ResponseEntity.ok(new ClassificationResponse(this.classifier.name(), result.rating()));
        } catch (final Exception exception) {
            logger.error("Rating request failed", exception);
            return ResponseEntity.internalServerError().body("internal server error");
        }
    }
}
