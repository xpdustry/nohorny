// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.xpdustry.nohorny.server.MindustryClientDirectory;
import com.xpdustry.nohorny.server.persistence.ClassificationRequest;
import com.xpdustry.nohorny.server.persistence.ClassificationStep;
import com.xpdustry.nohorny.server.persistence.Failure;
import com.xpdustry.nohorny.server.persistence.ImageState;
import com.xpdustry.nohorny.server.persistence.RatingBucket;
import com.xpdustry.nohorny.server.persistence.RequesterType;
import com.xpdustry.nohorny.server.persistence.Verdict;
import com.xpdustry.nohorny.server.security.SecurityConfiguration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/// The recorded classification requests. The list and the deletion are restricted to the administrators by the
/// security configuration, the rest is public to anyone holding the identifier.
@RestController
@RequestMapping("/api/requests")
public final class RequestController {

    private static final Logger log = LoggerFactory.getLogger(RequestController.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RequestService requests;
    private final MindustryClientDirectory clients;

    public RequestController(final RequestService requests, final MindustryClientDirectory clients) {
        this.requests = requests;
        this.clients = clients;
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public RequestView onGet(final @PathVariable String id, final @Nullable Authentication authentication) {
        return this.toView(
                this.requests.find(id).orElseThrow(RequestController::notFound),
                SecurityConfiguration.isAdmin(authentication));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> onGetImage(final @PathVariable String id) {
        final var request = this.requests.find(id).orElseThrow(RequestController::notFound);
        final var content = this.requests
                .findImage(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "image not available"));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(content);
    }

    @PostMapping(path = "/{id}/purge", produces = MediaType.APPLICATION_JSON_VALUE)
    public RequestView onPurge(final @PathVariable String id, final @Nullable Authentication authentication) {
        final var request = this.requests.purgeImage(id).orElseThrow(RequestController::notFound);
        log.info("Purged the image of request {}", id);
        return this.toView(request, SecurityConfiguration.isAdmin(authentication));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public RequestPage onList(
            final @RequestParam(required = false) @Nullable String rating,
            final @RequestParam(required = false) @Nullable String before,
            final @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int limit) {
        final var bucket = rating == null || rating.isEmpty()
                ? null
                : RatingBucket.fromKey(rating)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid rating"));
        final var cursor = before == null || before.isEmpty() ? null : parseIdentifier(before);
        final var size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        // One more to know whether there is a next page
        final var found = this.requests.findPage(bucket, cursor, size + 1);
        final var items = found.subList(0, Math.min(found.size(), size));
        return new RequestPage(
                items.stream().map(request -> this.toView(request, true)).toList(),
                found.size() > size ? items.getLast().getId() : null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void onDelete(final @PathVariable String id) {
        if (this.requests.delete(id)) {
            log.info("Deleted request {}", id);
        }
    }

    private RequestView toView(final ClassificationRequest request, final boolean admin) {
        // A Mindustry network requester was listed when it sent the request, the other clients are looked up
        final var requester = request.getRequester();
        final var client = requester.type() == RequesterType.MINDUSTRY_NETWORK
                ? new MindustryClientDirectory.ClientInfo(
                        MindustryClientDirectory.ClientInfo.MINDUSTRY_SERVER, requester.name())
                : this.clients.whois(request.getRemoteAddress());
        final var verdict = request.getOutcome() instanceof Verdict value ? value : null;
        final var failure = request.getOutcome() instanceof Failure value ? value : null;
        return new RequestView(
                request.getId(),
                request.getCreatedAt(),
                request.getDurationMillis(),
                verdict != null,
                verdict == null ? null : verdict.rating(),
                verdict == null ? null : verdict.confidence(),
                request.getClassifier(),
                failure == null ? null : failure.type(),
                request.getVersion(),
                new RequestView.Client(client.type(), client.network()),
                new RequestView.Image(
                        request.getImageState().key(),
                        request.getImageState() == ImageState.STORED
                                ? "/api/requests/" + request.getId() + "/image"
                                : null),
                request.getSteps().stream().map(step -> toView(step, admin)).toList(),
                admin ? restricted(request, failure) : null);
    }

    private static RequestView.Restricted restricted(
            final ClassificationRequest request, final @Nullable Failure failure) {
        final var requester = request.getRequester();
        return new RequestView.Restricted(
                request.getRemoteAddress(),
                new RequestView.Requester(requester.type().key(), requester.name()),
                failure == null ? null : failure.error());
    }

    private static RequestView.Step toView(final ClassificationStep step, final boolean admin) {
        return switch (step.outcome()) {
            case Verdict verdict ->
                new RequestView.Step(
                        step.classifier(), verdict.rating(), verdict.confidence(), step.durationMillis(), null, null);
            case Failure failure ->
                new RequestView.Step(
                        step.classifier(),
                        null,
                        null,
                        step.durationMillis(),
                        failure.type(),
                        admin ? failure.error() : null);
        };
    }

    private static String parseIdentifier(final String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (final IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid identifier");
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "request not found");
    }
}
