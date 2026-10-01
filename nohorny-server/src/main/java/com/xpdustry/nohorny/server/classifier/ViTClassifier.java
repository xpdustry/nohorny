// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import com.xpdustry.nohorny.server.natives.NativeClassifier;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.image.BufferedImage;
import java.util.StringJoiner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/// Runs an ONNX ViT model with the native classifiers. Since a native classifier is not
/// thread-safe, each one of them handles a single image at a time.
public final class ViTClassifier implements Classifier {

    private final ViTClassifierProperties properties;
    private final ViTModelSource source;
    private final BlockingQueue<NativeClassifier> classifiers;

    public ViTClassifier(final ViTClassifierProperties properties, final ViTModelSource source) {
        this.properties = properties;
        this.source = source;
        this.classifiers = new ArrayBlockingQueue<>(properties.instances());
    }

    @Override
    public String name() {
        return "vit/" + this.source.name();
    }

    @Override
    public Result classify(final BufferedImage image) throws InterruptedException {
        final var classifier = this.classifiers.take();
        final float[] probabilities;
        try {
            probabilities = classifier.classify(image);
        } finally {
            this.classifiers.add(classifier);
        }
        final var labels = this.properties.labels();
        if (probabilities.length != labels.size()) {
            throw new IllegalStateException(
                    "The model returned " + probabilities.length + " probabilities for the labels " + labels);
        }
        final var metadata = new StringJoiner(",", "{", "}");
        for (int i = 0; i < probabilities.length; i++) {
            metadata.add("\"" + labels.get(i) + "\":" + probabilities[i]);
        }
        final var score = probabilities[labels.indexOf(this.properties.nsfwLabel())];
        return new Result(this.properties.thresholds().apply(score), score, metadata.toString());
    }

    @PostConstruct
    void onInit() {
        final var file = this.source.retrieve();
        for (int i = 0; i < this.properties.instances(); i++) {
            this.classifiers.add(NativeClassifier.create(file));
        }
    }

    @PreDestroy
    void onExit() {
        this.classifiers.forEach(NativeClassifier::close);
    }
}
