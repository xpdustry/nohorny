// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.xpdustry.nohorny.server.MindustryClientDirectory;
import com.xpdustry.nohorny.server.persistence.ClassificationRequestSummary;
import com.xpdustry.nohorny.server.persistence.ImageState;
import com.xpdustry.nohorny.server.persistence.RatingBucket;
import com.xpdustry.nohorny.server.security.SecurityConfiguration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public ResponseEntity<byte[]> onGetImage(final @PathVariable String id) {
        final var image = this.requests.findImage(id).orElseThrow(RequestController::notFound);
        final var mediaType = image.mediaType();
        final var bytes = image.bytes();
        if (image.state() != ImageState.STORED || mediaType == null || bytes == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "image not available");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .cacheControl(CacheControl.noStore())
                .body(bytes.array());
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
                found.size() > size ? items.getLast().id() : null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void onDelete(final @PathVariable String id) {
        if (this.requests.delete(id)) {
            log.info("Deleted request {}", id);
        }
    }

    private RequestView toView(final ClassificationRequestSummary request, final boolean admin) {
        final var client = this.clients.whois(request.remoteAddress());
        return new RequestView(
                request.id(),
                request.createdAt(),
                request.durationMillis(),
                request.successful(),
                request.rating(),
                request.confidence(),
                request.classifier(),
                request.error(),
                request.version(),
                new RequestView.Client(client.type(), client.name()),
                new RequestView.Image(
                        request.imageState().key(),
                        request.imageMediaType(),
                        request.imageState() == ImageState.STORED ? "/api/requests/" + request.id() + "/image" : null),
                request.steps().stream()
                        .map(step -> new RequestView.Step(
                                step.classifier(),
                                step.rating(),
                                step.confidence(),
                                step.durationMillis(),
                                step.error()))
                        .toList(),
                admin ? new RequestView.Restricted(request.remoteAddress(), request.username()) : null);
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
