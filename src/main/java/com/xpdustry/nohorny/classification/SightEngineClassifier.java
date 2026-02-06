// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.classification;

import com.github.mizosoft.methanol.MediaType;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MoreBodyPublishers;
import com.github.mizosoft.methanol.MultipartBodyPublisher;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.xpdustry.nohorny.image.GraphicsScope;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SightEngineClassifier implements Classifier {

    private static final Logger logger = LoggerFactory.getLogger(SightEngineClassifier.class);

    private static final URI API_ENDPOINT = URI.create("https://api.sightengine.com/1.0/check.json");
    private static final String[] EXPLICIT_NUDITY_FIELDS = {"sexual_activity", "sexual_display", "sextoy", "erotica"};

    private final Methanol http;
    private final Gson gson;
    private final Executor executor;
    private final String userid;
    private final String secret;
    private final Thresholds thresholds;

    public SightEngineClassifier(
            final Methanol http,
            final Gson gson,
            Executor executor,
            final String userid,
            final String secret,
            final Thresholds thresholds) {
        this.http = http;
        this.gson = gson;
        this.executor = executor;
        this.userid = userid;
        this.secret = secret;
        this.thresholds = thresholds;
    }

    @Override
    public String name() {
        return "sight-engine";
    }

    @Override
    public Thresholds thresholds() {
        return this.thresholds;
    }

    @Override
    public String version() {
        return "nudity-2.1";
    }

    @Override
    public Classifier.Result classify(final BufferedImage image) throws Exception {
        final var response = this.http.send(
                HttpRequest.newBuilder(API_ENDPOINT)
                        .POST(MultipartBodyPublisher.newBuilder()
                                .textPart("api_user", this.userid)
                                .textPart("api_secret", this.secret)
                                .textPart("models", this.version())
                                .formPart(
                                        "media",
                                        "image.jpg",
                                        MoreBodyPublishers.ofMediaType(
                                                MoreBodyPublishers.ofOutputStream(
                                                        out -> writeJpgTo(out, image), this.executor),
                                                MediaType.IMAGE_JPEG))
                                .build())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        final var json = this.gson.fromJson(response.body(), JsonObject.class);
        if (response.statusCode() != 200) {
            final var error = json.getAsJsonObject().get("error");
            logger.error("SightEngine API returned error: {}", error);
            throw new IOException("SightEngine API returned " + response.statusCode() + ": " + error);
        }

        logger.trace("SightEngine response: {}", json);
        if (!json.get("status").getAsString().contentEquals("success")) {
            logger.error("SightEngine API returned error: {}", json.get("error"));
            throw new IOException("SightEngine API returned unsuccessful status");
        }

        final var score = Arrays.stream(EXPLICIT_NUDITY_FIELDS)
                .mapToDouble(field -> {
                    final var nudity = json.getAsJsonObject("nudity");
                    return nudity.has(field) ? nudity.get(field).getAsDouble() : 0D;
                })
                .max()
                .orElse(0);

        return new Classifier.Result(this.thresholds.apply(score), json);
    }

    private void writeJpgTo(final OutputStream out, final BufferedImage image) throws IOException {
        var result = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            try (final var scope = new GraphicsScope(result)) {
                scope.graphics().drawImage(image, 0, 0, null);
            }
        }
        ImageIO.write(result, "jpg", out);
    }
}
