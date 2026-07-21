// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.persistence.RequestProperties;
import com.xpdustry.nohorny.server.MindustryClientDirectory.ClientInfo;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin")
public final class AdminController {

    private final ClassificationRequestRepository requests;
    private final RequestProperties requestProperties;
    private final MindustryClientDirectory clients;

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
}
