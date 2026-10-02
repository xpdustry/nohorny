// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.natives;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/// A ViT image classifier running an ONNX model with OpenCV. Not thread-safe.
public final class NativeClassifier implements AutoCloseable {

    private static final int MAX_LABELS = 64;

    private final MemorySegment handle;
    private boolean closed = false;

    private NativeClassifier(final MemorySegment handle) {
        this.handle = handle;
    }

    public static NativeClassifier create(final Path model) {
        try (final var arena = Arena.ofConfined()) {
            final var handle = NoHornyNative.bindings()
                    .classifierCreate()
                    .call(arena.allocateFrom(model.toAbsolutePath().toString()));
            if (handle.equals(MemorySegment.NULL)) {
                throw NoHornyNative.lastError();
            }
            return new NativeClassifier(handle);
        }
    }

    /// Returns the softmax probabilities of each label, in the order of the model outputs.
    public float[] classify(final BufferedImage image) {
        if (this.closed) {
            throw new IllegalStateException("The classifier is closed");
        }
        final var w = image.getWidth();
        final var h = image.getHeight();
        try (final var arena = Arena.ofConfined()) {
            // getRGB always returns packed ARGB ints, whatever the color model of the image
            final var pixels = arena.allocateFrom(JAVA_INT, image.getRGB(0, 0, w, h, null, 0, w));
            final var scores = arena.allocate(JAVA_FLOAT, MAX_LABELS);
            final var count =
                    NoHornyNative.bindings().classifierClassify().call(this.handle, pixels, w, h, scores, MAX_LABELS);
            if (count < 0) {
                throw NoHornyNative.lastError();
            }
            if (count > MAX_LABELS) {
                throw new NativeException("The model has more than " + MAX_LABELS + " labels: " + count, null);
            }
            return scores.asSlice(0, JAVA_FLOAT.byteSize() * count).toArray(JAVA_FLOAT);
        }
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        NoHornyNative.bindings().classifierClose().call(this.handle);
    }
}
