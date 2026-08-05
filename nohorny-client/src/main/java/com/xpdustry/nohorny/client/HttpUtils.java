// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public static URI appendPathSegments(final URI base, final String... segments) {
        final var path = base.getPath()
                + (base.getPath().endsWith("/") ? "" : "/")
                + Stream.of(segments)
                        .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining("/"));
        try {
            return new URI(
                    base.getScheme(),
                    base.getUserInfo(),
                    base.getHost(),
                    base.getPort(),
                    path,
                    base.getQuery(),
                    base.getFragment());
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException(
                    "Failed to append path segments " + String.join("/", segments) + " to " + base, e);
        }
    }

    private HttpUtils() {}

    @FunctionalInterface
    public interface IOConsumer<T> {

        void accept(final T value) throws IOException;
    }
}
