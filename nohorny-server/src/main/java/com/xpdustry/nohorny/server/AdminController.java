// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.persistence.RequestProperties;
import com.xpdustry.nohorny.server.MindustryClientDirectory.ClientInfo;
import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
@RequestMapping("/admin")
public final class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final ClassificationRequestRepository requests;
    private final RequestProperties requestProperties;
    private final MindustryClientDirectory clients;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public AdminController(
            final ClassificationRequestRepository requests,
            final RequestProperties requestProperties,
            final MindustryClientDirectory clients) {
        this.requests = requests;
        this.requestProperties = requestProperties;
        this.clients = clients;
    }

    @GetMapping
    public String index() {
        return "redirect:/admin/index.html";
    }

    @ResponseBody
    @GetMapping("/api/requests")
    public List<ClassificationRequestView> recentRequests() {
        return this.requests.findAllByOrderByIdDesc(Limit.of(this.requestProperties.capacity())).stream()
                .map(summary -> ClassificationRequestView.of(summary, this.whois(summary.getRemoteAddress())))
                .toList();
    }

    private ClientInfo whois(final String remoteAddress) {
        try {
            return this.clients.whois(remoteAddress);
        } catch (final UnknownHostException exception) {
            return new ClientInfo("unknown", remoteAddress);
        }
    }

    @ResponseBody
    @GetMapping("/api/requests/{id}/image")
    public ResponseEntity<byte[]> requestImage(final @PathVariable long id) {
        return this.requests
                .findImageById(id)
                .map(image -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .header("Content-Type", image.getImageMediaType())
                        .body(image.getImage()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Server-sent events stream that emits an event whenever a new request is classified. */
    @GetMapping("/api/stream")
    public SseEmitter stream() {
        final var emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        this.emitters.add(emitter);
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> {
            this.emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(_ -> this.emitters.remove(emitter));
        try {
            // A priming comment opens the stream immediately and confirms the connection to the client.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (final IOException exception) {
            this.emitters.remove(emitter);
        }
        return emitter;
    }

    @EventListener
    public void onRequestSaved(final ClassificationRequestSavedEvent event) {
        final var payload = SseEmitter.event().name("request").data(Long.toString(event.id()));
        final List<SseEmitter> stale = new ArrayList<>();
        for (final var emitter : this.emitters) {
            try {
                emitter.send(payload);
            } catch (final IOException | IllegalStateException exception) {
                stale.add(emitter);
                log.debug("Dropped a disconnected admin stream subscriber", exception);
            }
        }
        stale.forEach(this.emitters::remove);
    }
}
