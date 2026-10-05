// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class HttpUtils {

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
}
