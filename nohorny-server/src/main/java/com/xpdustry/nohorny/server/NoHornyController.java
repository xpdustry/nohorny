// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.common.SimpleServerMessage;
import com.xpdustry.nohorny.server.request.RetentionController;
import com.xpdustry.nohorny.server.security.SecurityConfiguration;
import com.xpdustry.nohorny.server.security.SessionController;
import com.xpdustry.nohorny.server.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

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
    private final SentryProperties sentry;
    private final RequestProperties requests;
    private final UserService users;
    private final JsonMapper mapper;

    public NoHornyController(
            final StatusProperties status,
            final ClassificationService classifications,
            final RequesterResolver requesters,
            final SentryProperties sentry,
            final RequestProperties requests,
            final UserService users,
            final JsonMapper mapper) {
        this.status = status;
        this.classifications = classifications;
        this.requesters = requesters;
        this.sentry = sentry;
        this.requests = requests;
        this.users = users;
        this.mapper = mapper;
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
                        SecurityConfiguration.isAuthenticated(authentication),
                        request.getRemoteAddr()));
    }

    /// The runtime configuration of the pages, loaded by a blocking script before their own. It holds the session of
    /// the caller, so the pages know it without a request of their own. Other sites must not load it.
    @GetMapping(path = "/config.js", produces = "text/javascript")
    public ResponseEntity<String> onConfig(final @Nullable Authentication authentication) {
        final var config = new PageConfig(
                this.sentry.dsn(),
                SecurityConfiguration.isAuthenticated(authentication)
                        ? SessionController.Session.of(authentication, this.users)
                        : null,
                RetentionController.Retention.of(this.requests));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body("window.NOHORNY_CONFIG = " + this.mapper.writeValueAsString(config) + ";\n");
    }

    /// Read by nohorny-frontend/src/lib/config.ts.
    private record PageConfig(
            @Nullable String sentryDsn,
            SessionController.@Nullable Session session,
            RetentionController.Retention retention) {}

    @GetMapping(path = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String onRobots() {
        return ROBOTS;
    }
}
