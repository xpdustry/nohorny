// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.SimpleServerMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class NoHornyController {

    static final String VERSION_HEADER = "X-NoHorny-Version";

    private static final String ROBOTS = """
            User-agent: *
            Disallow: /requests/
            Disallow: /api/
            Disallow: /admin
            """;

    private final StatusProperties status;
    private final ClassificationService classifications;
    private final RequesterResolver requesters;

    public NoHornyController(
            final StatusProperties status,
            final ClassificationService classifications,
            final RequesterResolver requesters) {
        this.status = status;
        this.classifications = classifications;
        this.requesters = requesters;
    }

    @GetMapping(path = "/api/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleServerMessage onStatus() {
        return new SimpleServerMessage(this.status.motd());
    }

    @PostMapping(
            path = "/api/classify",
            consumes = MediaType.IMAGE_JPEG_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> onClassify(
            final @RequestBody byte[] body,
            final @RequestHeader(name = VERSION_HEADER, required = false) @Nullable String version,
            final @Nullable Authentication authentication,
            final HttpServletRequest request) {
        return this.classifications.classify(
                body,
                new ClassificationService.Submission(
                        version,
                        this.requesters.resolve(authentication, request.getRemoteAddr()),
                        request.getRemoteAddr()));
    }

    @GetMapping(path = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String onRobots() {
        return ROBOTS;
    }
}
