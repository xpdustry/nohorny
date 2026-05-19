// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// AIGEN
final class MultipartFormBodyPublisherTest {

    @Test
    void serialize_text_and_file_parts() {
        final var body = new MultipartFormBodyPublisher.Builder()
                .boundary("my_boundary")
                .textPart("payload_json", "{\"content\":\"Hello\"}")
                .formPart("files[0]", "image.png", "image/png", HttpRequest.BodyPublishers.ofString("png-bytes"))
                .build();

        assertEquals("multipart/form-data; boundary=my_boundary", body.contentType());
        assertEquals(
                crlf(
                        "--my_boundary",
                        "Content-Disposition: form-data; name=\"payload_json\"",
                        "Content-Type: text/plain; charset=utf-8",
                        "",
                        "{\"content\":\"Hello\"}",
                        "--my_boundary",
                        "Content-Disposition: form-data; name=\"files[0]\"; filename=\"image.png\"",
                        "Content-Type: image/png",
                        "",
                        "png-bytes",
                        "--my_boundary--"),
                read(body));
    }

    @Test
    void escape_content_disposition_parameters() {
        final var body = new MultipartFormBodyPublisher.Builder()
                .boundary("my_boundary")
                .formPart("field\\name", "\"file\\name\"", "text/plain", HttpRequest.BodyPublishers.ofString("x"))
                .build();

        assertEquals(
                crlf(
                        "--my_boundary",
                        "Content-Disposition: form-data; name=\"field\\\\name\"; filename=\"\\\"file\\\\name\\\"\"",
                        "Content-Type: text/plain",
                        "",
                        "x",
                        "--my_boundary--"),
                read(body));
    }

    private static String crlf(final String... lines) {
        return String.join(MultipartFormBodyPublisher.CRLF, lines) + MultipartFormBodyPublisher.CRLF;
    }

    private static String read(final BodyPublisher publisher) {
        final var result = new CompletableFuture<String>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private final ByteArrayOutputStream output = new ByteArrayOutputStream();

            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final ByteBuffer item) {
                final var bytes = new byte[item.remaining()];
                item.get(bytes);
                this.output.writeBytes(bytes);
            }

            @Override
            public void onError(final Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.complete(this.output.toString(StandardCharsets.UTF_8));
            }
        });
        return result.join();
    }
}
