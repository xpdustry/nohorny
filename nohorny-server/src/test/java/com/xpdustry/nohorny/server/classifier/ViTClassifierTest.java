// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.natives.NativeClassifier;
import com.xpdustry.nohorny.natives.NativeException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ViTClassifierTest {

    // Downloaded by Gradle
    private static final Path MODEL = Path.of(Objects.requireNonNull(System.getProperty("nohorny.test.vit-model")));

    @Test
    void classifySafeImage() throws InterruptedException {
        final var classifier = new ViTClassifier(
                new ViTClassifierProperties(List.of("normal", "nsfw"), "nsfw", 1, new ThresholdProperties(0.6, 0.9)),
                new LocalViTModelSource(new LocalViTModelSourceProperties(MODEL)));
        classifier.onInit();
        try {
            final var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            final var graphics = image.createGraphics();
            graphics.setColor(Color.ORANGE);
            graphics.fillRect(0, 0, 64, 64);
            graphics.dispose();
            assertEquals(Rating.SAFE, classifier.classify(image).rating());
        } finally {
            classifier.onExit();
        }
    }

    @Test
    void createWithMissingModel() {
        assertThrows(NativeException.class, () -> NativeClassifier.create(Path.of("missing.onnx")));
    }
}
