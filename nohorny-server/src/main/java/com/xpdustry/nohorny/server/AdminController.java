// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.server.MindustryClientDirectory.ClientInfo;
import java.net.UnknownHostException;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin")
public final class AdminController {

    private static final int PAGE_SIZE = 20;

    private final ClassificationRequestRepository requests;
    private final MindustryClientDirectory clients;

    public AdminController(final ClassificationRequestRepository requests, final MindustryClientDirectory clients) {
        this.requests = requests;
        this.clients = clients;
    }

    @GetMapping
    public String index() {
        return "redirect:/admin/index.html";
    }

    @ResponseBody
    @GetMapping("/api/requests")
    public ClassificationRequestPage recentRequests(final @RequestParam(required = false) @Nullable Long before) {
        final var summaries = before == null
                ? this.requests.findAllByOrderByIdDesc(Limit.of(PAGE_SIZE + 1))
                : this.requests.findAllByIdLessThanOrderByIdDesc(before, Limit.of(PAGE_SIZE + 1));
        final var hasMore = summaries.size() > PAGE_SIZE;
        final var items = summaries.stream()
                .limit(PAGE_SIZE)
                .map(summary -> ClassificationRequestView.of(summary, this.whois(summary.getRemoteAddress())))
                .toList();
        return new ClassificationRequestPage(items, hasMore ? items.getLast().id() : null);
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
}
