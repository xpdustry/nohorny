// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;

public final class HuggingFaceViTModelSource implements ViTModelSource {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceViTModelSource.class);

    private final HuggingFaceViTModelSourceProperties properties;
    private final RestClient restClient;

    public HuggingFaceViTModelSource(
            final HuggingFaceViTModelSourceProperties properties, final RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public String name() {
        return this.properties.repository() + ":" + this.properties.revision() + "/" + this.properties.file();
    }

    @Override
    public Path retrieve() {
        final var name = this.name().replace('/', '-').replace(':', '-');
        final var target = this.properties.downloadDirectory().resolve(name);
        if (Files.notExists(target)) {
            try {
                Files.createDirectories(this.properties.downloadDirectory());
            } catch (final IOException e) {
                throw new RuntimeException(
                        "Failed to create the download directory for hugging face models: "
                                + this.properties.downloadDirectory(),
                        e);
            }
            final var url = "https://huggingface.co/" + this.properties.repository() + "/resolve/"
                    + this.properties.revision() + "/" + this.properties.file();
            log.info("Model {} does not exist locally, downloading from hugging face at {}", name, url);
            final var request = this.restClient.get().uri(url);
            if (this.properties.token() != null) {
                request.header("Authorization", "Bearer " + this.properties.token());
            }
            final var resource = request.retrieve().requiredBody(Resource.class);
            final var temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            try (final var in = resource.getInputStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target);
            } catch (final IOException e) {
                try {
                    Files.deleteIfExists(temp);
                } catch (final IOException _) {
                }
                throw new RuntimeException("Failed to copy hugging face model " + name, e);
            }
        }
        return target;
    }
}
