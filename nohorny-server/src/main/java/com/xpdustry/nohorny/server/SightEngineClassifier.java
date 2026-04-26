// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.GraphicsScope;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

public final class SightEngineClassifier implements Classifier {

    private static final URI API_ENDPOINT = URI.create("https://api.sightengine.com/1.0/check.json");
    private static final String MODELS = "nudity-2.1";
    private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(1);

    private final RestClient restClient;
    private final SightEngineClassifierProperties properties;
    private final JsonMapper jsonMapper;
    private final Object lock = new Object();
    private Instant nextRequestAt = Instant.MIN;

    public SightEngineClassifier(
            final RestClient restClient,
            final SightEngineClassifierProperties properties,
            final JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String name() {
        return "sight-engine";
    }

    @Override
    public Result classify(final BufferedImage image) throws Exception {
        this.waitIfRateLimited();
        final var request = new LinkedMultiValueMap<String, Object>();
        request.add("api_user", this.properties.user());
        request.add("api_secret", this.properties.secret());
        request.add("models", MODELS);
        request.add("media", new HttpEntity<>(jpegResource(writeJpg(image)), jpegHeaders()));
        final var response = this.restClient
                .post()
                .uri(API_ENDPOINT)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(request)
                .retrieve()
                .requiredBody(SightEngineResponse.class);
        if (!"success".equals(response.status())) {
            throw new IOException("SightEngine API returned error or missing nudity data: "
                    + this.jsonMapper.writeValueAsString(response));
        }
        final var score = response.nudity().maxScore();
        return new Result(this.properties.thresholds().apply(score), this.jsonMapper.writeValueAsString(response));
    }

    private void waitIfRateLimited() throws InterruptedException {
        final var now = Instant.now();
        final Instant sendAt;
        synchronized (this.lock) {
            sendAt = now.isAfter(this.nextRequestAt) ? now : this.nextRequestAt;
            this.nextRequestAt = sendAt.plus(REQUEST_INTERVAL);
        }
        final var timeToWait = Duration.between(now, sendAt);
        if (timeToWait.isPositive()) {
            Thread.sleep(timeToWait.toMillis());
        }
    }

    private static HttpHeaders jpegHeaders() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        return headers;
    }

    private static ByteArrayResource jpegResource(final byte[] jpg) {
        return new ByteArrayResource(jpg) {
            @Override
            public String getFilename() {
                return "image.jpg";
            }
        };
    }

    // TODO Use a temporary file instead
    private static byte[] writeJpg(final BufferedImage image) throws IOException {
        var result = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            try (final var scope = new GraphicsScope(result)) {
                scope.graphics().drawImage(image, 0, 0, null);
            }
        }

        final var output = new ByteArrayOutputStream();
        if (!ImageIO.write(result, "jpg", output)) {
            throw new IOException("Failed to encode image as JPEG for SightEngine");
        }
        return output.toByteArray();
    }

    // https://sightengine.com/docs/advanced-nudity-detection-model-2.1
    record SightEngineResponse(String status, Request request, Nudity nudity) {
        record Request(String id) {}

        record Nudity(double erotica, double very_suggestive, double sexual_display) {
            public double maxScore() {
                return Math.max(very_suggestive, Math.max(sexual_display, erotica));
            }
        }
    }
}
