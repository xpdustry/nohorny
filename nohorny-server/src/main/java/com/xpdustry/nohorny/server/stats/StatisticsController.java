// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public final class StatisticsController {

    private final StatisticsService statistics;

    public StatisticsController(final StatisticsService statistics) {
        this.statistics = statistics;
    }

    @GetMapping(path = "/api/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Statistics onStatistics() {
        return this.statistics.compute();
    }

    @GetMapping(path = "/api/stats/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public History onHistory(final @RequestParam(defaultValue = "30d") String range) {
        return this.statistics.history(parseRange(range));
    }

    /// Restricted to the administrators by the security configuration.
    @GetMapping(path = "/api/stats/networks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Networks onNetworks(final @RequestParam(defaultValue = "30d") String range) {
        return this.statistics.networks(parseRange(range));
    }

    /// Restricted to the administrators by the security configuration.
    @GetMapping(path = "/api/stats/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    public Versions onVersions(final @RequestParam(defaultValue = "30d") String range) {
        return this.statistics.versions(parseRange(range));
    }

    private static Range parseRange(final String key) {
        return Range.fromKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown range"));
    }
}
