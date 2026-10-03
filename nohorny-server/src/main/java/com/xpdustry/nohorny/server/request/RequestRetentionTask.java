// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.xpdustry.nohorny.server.RequestProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// Deletes the expired images and requests, on startup then hourly.
@Component
public final class RequestRetentionTask {

    private static final Logger log = LoggerFactory.getLogger(RequestRetentionTask.class);

    private final RequestService requests;
    private final RequestProperties properties;

    public RequestRetentionTask(final RequestService requests, final RequestProperties properties) {
        this.requests = requests;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "1h")
    public void run() {
        try {
            final var now = Instant.now();
            final var images = this.requests.expireImagesBefore(now.minus(this.properties.imageRetention()));
            final var deleted = this.requests.deleteCreatedBefore(now.minus(this.properties.retention()));
            if (images > 0 || deleted > 0) {
                log.info("Expired {} images and deleted {} requests", images, deleted);
            }
        } catch (final RuntimeException exception) {
            log.error("Failed to apply the request retention", exception);
        }
    }
}
