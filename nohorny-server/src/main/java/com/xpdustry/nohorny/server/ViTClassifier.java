// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ViTClassifier implements Classifier {

    public enum Engine {
        PYTORCH("PyTorch"),
        ONNX("OnnxRuntime");

        private final String djlName;

        Engine(final String djlName) {
            this.djlName = djlName;
        }

        public String djlName() {
            return this.djlName;
        }
    }

    private final String repository;
    private final String revision;
    private final String file;
    private final @Nullable String token;
    private final List<String> labels;
    private final String nsfwLabel;
    private final HttpClient http;
    private final Path directory;
    private final Thresholds thresholds;
    private final Engine engine;

    private @Nullable ZooModel<Image, Classifications> model;

    public ViTClassifier(
            final String repository,
            final String revision,
            final String file,
            final @Nullable String token,
            final List<String> labels,
            final String nsfwLabel,
            final HttpClient http,
            final Path directory,
            final Thresholds thresholds,
            final Engine engine) {
        this.repository = repository;
        this.revision = revision;
        this.file = file;
        this.token = token;
        this.labels = labels;
        this.nsfwLabel = nsfwLabel;
        this.http = http;
        this.directory = directory;
        this.thresholds = thresholds;
        this.engine = engine;
    }

    @Override
    public String name() {
        return "vit/" + this.repository + ":" + this.revision + "/" + this.file;
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
        final var name = this.name().replace('/', '-');
        final var target = this.directory.resolve(name);
        if (Files.notExists(target)) {
            final var url = "https://huggingface.co/" + this.repository + "/resolve/" + this.revision + "/" + this.file;
            final var request = HttpRequest.newBuilder(URI.create(url)).GET();
            if (this.token != null) {
                request.header("Authorization", "Bearer " + this.token);
            }
            final HttpResponse<?> response;
            try {
                response = this.http.send(request.build(), HttpResponse.BodyHandlers.ofFile(target));
            } catch (final IOException | InterruptedException e) {
                throw new RuntimeException("Failed to retrieve hugging face model " + name, e);
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Download failed for " + this.file + ": HTTP " + response.statusCode());
            }
        }
        try {
            this.model = Criteria.builder()
                    .setTypes(Image.class, Classifications.class)
                    .optModelPath(target)
                    .optEngine(this.engine.djlName())
                    .optTranslator(new ViTImageTranslator())
                    .build()
                    .loadModel();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to load model from " + target, e);
        }
    }

    @Override
    public void onExit() {
        if (this.model != null) {
            this.model.close();
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
