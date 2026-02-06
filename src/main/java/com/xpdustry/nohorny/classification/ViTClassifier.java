// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.classification;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ViTClassifier implements Classifier {

    private @Nullable ZooModel<Image, Classifications> model;
    private final List<String> labels;
    private final String nsfwLabel;
    private final Path path;
    private final Thresholds thresholds;

    public ViTClassifier(
            final List<String> labels, final String nsfwLabel, final Path path, final Thresholds thresholds) {
        this.labels = labels;
        this.nsfwLabel = nsfwLabel;
        this.path = path;
        this.thresholds = thresholds;
    }

    @Override
    public String name() {
        return "vit";
    }

    @Override
    public Thresholds thresholds() {
        return this.thresholds;
    }

    @Override
    public String version() {
        return this.path.getFileName().toString();
    }

    @Override
    public Result classify(final BufferedImage image) throws Exception {
        final var model = Objects.requireNonNull(this.model, "ViTClassifier has not been initialized");
        final var converted = ImageFactory.getInstance().fromImage(image);
        try (final var predictor = model.newPredictor()) {
            final var prediction = predictor.predict(converted);
            final var score = prediction.get(this.nsfwLabel).getProbability();
            return new Result(this.thresholds.apply(score), prediction.serialize());
        }
    }

    @Override
    public void onInit() {
        if (Files.notExists(this.path)) {
            throw new IllegalStateException("The model file at " + this.path + " does not exist");
        }
        final var cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            this.model = Criteria.builder()
                    .setTypes(Image.class, Classifications.class)
                    .optModelPath(this.path)
                    .optEngine("PyTorch")
                    .optTranslator(new ViTImageTranslator())
                    .build()
                    .loadModel();
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to load model from " + path, e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    @Override
    public void onExit() {
        if (this.model != null) {
            this.model.close();
            this.model = null;
        }
    }

    private final class ViTImageTranslator implements Translator<Image, Classifications> {

        private static final float[] DOT_FIVE = {0.5F, 0.5F, 0.5F};

        @Override
        public NDList processInput(final TranslatorContext ctx, final Image input) {
            var array = input.toNDArray(ctx.getNDManager());
            array = NDImageUtils.resize(array, 224, 224); // Model expects 224 by 244 images
            array = NDImageUtils.toTensor(array);
            array = NDImageUtils.normalize(array, DOT_FIVE, DOT_FIVE); // Model expects simple .5 mean and std
            return new NDList(array);
        }

        @Override
        public Classifications processOutput(final TranslatorContext ctx, final NDList list) {
            var result = list.singletonOrThrow();
            result = result.squeeze(0); // Remove Batch dimension
            result = result.softmax(0); // Convert to [0, 1] probabilities
            return new Classifications(ViTClassifier.this.labels, result);
        }
    }
}
