// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.http.HttpRequest;
import java.util.concurrent.Executor;

final class HttpUtils {

    private static final MiniLogger log = MiniLogger.forClass(HttpUtils.class);

    public static HttpRequest.BodyPublisher ofOutputStream(
            final Executor executor, final IOConsumer<OutputStream> consumer) {
        return HttpRequest.BodyPublishers.ofInputStream(() -> {
            final var in = new PipedInputStream(4 * 1024);
            final PipedOutputStream out;
            try {
                out = new PipedOutputStream(in);
            } catch (final IOException e) {
                throw new IllegalStateException("Failed to connect request body pipe", e);
            }
            executor.execute(() -> {
                try (out) {
                    consumer.accept(out);
                } catch (final IOException e) {
                    log.error("Failed to stream request body to pipe", e);
                }
            });
            return in;
        });
    }

    private HttpUtils() {}

    @FunctionalInterface
    public interface IOConsumer<T> {

        void accept(final T value) throws IOException;
    }
}
