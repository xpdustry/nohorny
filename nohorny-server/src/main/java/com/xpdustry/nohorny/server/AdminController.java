// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.persistence.ClassificationRequestSummary;
import com.xpdustry.nohorny.persistence.RequestProperties;
import com.xpdustry.nohorny.persistence.RequestRepository;
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

    private final RequestRepository requests;
    private final RequestProperties requestProperties;

    public AdminController(final RequestRepository requests, final RequestProperties requestProperties) {
        this.requests = requests;
        this.requestProperties = requestProperties;
    }

    @GetMapping
    public String index() {
        return "redirect:/admin/index.html";
    }

    @ResponseBody
    @GetMapping("/api/requests")
    public List<ClassificationRequestSummary> recentRequests() {
        return this.requests.findAllByOrderByIdDesc(Limit.of(this.requestProperties.capacity()));
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
