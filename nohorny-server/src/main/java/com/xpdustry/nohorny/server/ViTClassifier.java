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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class ViTClassifier implements Classifier {

    private static final Logger logger = LoggerFactory.getLogger(ViTClassifier.class);
    private final RestClient restClient;
    private final ViTClassifierProperties properties;

    private @Nullable ZooModel<Image, Classifications> model;

    @Autowired
    public ViTClassifier(final RestClient restClient, final ViTClassifierProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "vit/" + this.properties.repository() + ":" + this.properties.revision() + "/" + this.properties.file();
    }

    @Override
    public Result classify(final BufferedImage image) throws Exception {
        final var model = Objects.requireNonNull(this.model, "ViTClassifier has not been initialized");
        final var converted = ImageFactory.getInstance().fromImage(image);
        try (final var predictor = model.newPredictor()) {
            final var prediction = predictor.predict(converted);
            final var score = prediction.get(this.properties.nsfwLabel()).getProbability();
            return new Result(this.properties.thresholds().apply(score), prediction.serialize());
        }
    }

    @PostConstruct
    void onInit() {
        final var name = this.name().replace('/', '-').replace(':', '-');
        final var target = this.properties.directory().resolve(name);
        if (Files.notExists(target)) {
            final var url = "https://huggingface.co/" + this.properties.repository() + "/resolve/"
                    + this.properties.revision() + "/" + this.properties.file();
            logger.info("Model {} does not exists locally, downloading from hugging face at {}", name, url);
            final var request = this.restClient.get().uri(url);
            if (this.properties.token() != null) {
                request.header("Authorization", "Bearer " + this.properties.token());
            }
            final var resource = request.retrieve().requiredBody(Resource.class);
            try (final var in = resource.getInputStream()) {
                Files.createDirectories(target.getParent());
                Files.copy(in, target);
            } catch (final IOException e) {
                throw new RuntimeException("Failed to copy hugging face model " + name, e);
            }
        }
        try {
            this.model = Criteria.builder()
                    .setTypes(Image.class, Classifications.class)
                    .optModelPath(target)
                    .optEngine(this.properties.engine())
                    .optTranslator(new ViTImageTranslator())
                    .build()
                    .loadModel();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to load model from " + target, e);
        }
    }

    @PreDestroy
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
            return new Classifications(ViTClassifier.this.properties.labels(), result);
        }
    }
}
