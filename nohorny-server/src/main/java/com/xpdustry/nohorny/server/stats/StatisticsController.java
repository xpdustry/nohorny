// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public final class StatisticsController {

    private final StatisticsService statistics;
    private final StatisticsBroadcaster broadcaster;

    public StatisticsController(final StatisticsService statistics, final StatisticsBroadcaster broadcaster) {
        this.statistics = statistics;
        this.broadcaster = broadcaster;
    }

    @GetMapping(path = "/api/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Statistics onStatistics() {
        return this.statistics.compute();
    }

    @GetMapping(path = "/api/stats/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public History onHistory(final @RequestParam(defaultValue = "30d") String range) {
        return this.statistics
                .history(range)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown range"));
    }

    /// Restricted to the administrators by the security configuration.
    @GetMapping(path = "/api/stats/networks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Networks onNetworks(final @RequestParam(defaultValue = "30d") String range) {
        return this.statistics
                .networks(range)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown range"));
    }

    /// Restricted to the administrators by the security configuration.
    @GetMapping(path = "/api/stats/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    public Versions onVersions(final @RequestParam(defaultValue = "30d") String range) {
        return this.statistics
                .versions(range)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown range"));
    }

    @GetMapping(path = "/api/stats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter onStream() throws IOException {
        return this.broadcaster.subscribe();
    }
}
