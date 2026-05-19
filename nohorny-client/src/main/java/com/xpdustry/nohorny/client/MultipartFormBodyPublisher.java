// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Flow;
import org.jspecify.annotations.Nullable;

final class MultipartFormBodyPublisher implements BodyPublisher {

    static final String CRLF = "\r\n";

    private final String boundary;
    private final BodyPublisher delegate;

    private MultipartFormBodyPublisher(final @Nullable String boundary, final List<Part> parts) {
        if (boundary != null) {
            this.boundary = boundary;
        } else {
            final var bytes = new byte[16];
            new Random().nextBytes(bytes);
            this.boundary =
                    "----NoHornyFormBoundary" + HexFormat.of().withUpperCase().formatHex(bytes);
        }
        final var publishers = new ArrayList<BodyPublisher>(parts.size() * 2 + 1);
        for (int index = 0; index < parts.size(); index++) {
            final var part = parts.get(index);
            publishers.add(BodyPublishers.ofString(this.createPartHeader(index == 0, part)));
            publishers.add(part.body());
        }
        publishers.add(BodyPublishers.ofString(CRLF + "--" + this.boundary + "--" + CRLF));
        this.delegate = BodyPublishers.concat(publishers.toArray(BodyPublisher[]::new));
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + this.boundary;
    }

    @Override
    public long contentLength() {
        return this.delegate.contentLength();
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
        this.delegate.subscribe(subscriber);
    }

    private String createPartHeader(final boolean first, final Part part) {
        final var header = new StringBuilder();
        if (!first) {
            header.append(CRLF);
        }

        header.append("--").append(this.boundary).append(CRLF);

        header.append("Content-Disposition: form-data; name=");
        appendQuoted(header, part.name());
        if (part.filename() != null) {
            header.append("; filename=");
            appendQuoted(header, part.filename());
        }
        header.append(CRLF);

        if (part.contentType() != null) {
            header.append("Content-Type: ").append(part.contentType()).append(CRLF);
        }

        header.append(CRLF);
        return header.toString();
    }

    private static void appendQuoted(final StringBuilder builder, final String value) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            final var character = value.charAt(index);
            if (character == '"' || character == '\\') {
                builder.append('\\');
            }
            builder.append(character);
        }
        builder.append('"');
    }

    private record Part(
            String name,
            @Nullable String filename,
            @Nullable String contentType,
            BodyPublisher body) {}

    static final class Builder {

        private final List<Part> parts = new ArrayList<>();
        private @Nullable String boundary = null;

        public Builder textPart(final String name, final String value) {
            this.parts.add(new Part(name, null, "text/plain; charset=utf-8", BodyPublishers.ofString(value)));
            return this;
        }

        public Builder formPart(
                final String name, final String filename, final String contentType, final BodyPublisher body) {
            this.parts.add(new Part(name, filename, contentType, body));
            return this;
        }

        public Builder boundary(final String boundary) {
            this.boundary = boundary;
            return this;
        }

        public MultipartFormBodyPublisher build() {
            return new MultipartFormBodyPublisher(this.boundary, List.copyOf(this.parts));
        }
    }
}
