// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.xpdustry.nohorny.server.RequestProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/// The retention periods of this server, shown on the privacy page.
@RestController
public final class RetentionController {

    private final RequestProperties properties;

    public RetentionController(final RequestProperties properties) {
        this.properties = properties;
    }

    @GetMapping(path = "/api/retention", produces = MediaType.APPLICATION_JSON_VALUE)
    public Retention onRetention() {
        return new Retention(
                this.properties.imageRetention().toMillis(),
                this.properties.retention().toMillis());
    }

    public record Retention(long imageMillis, long requestMillis) {}
}
