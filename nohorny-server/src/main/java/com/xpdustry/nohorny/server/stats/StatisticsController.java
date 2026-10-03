// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
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

    @GetMapping(path = "/api/stats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter onStream() throws IOException {
        return this.broadcaster.subscribe();
    }
}
