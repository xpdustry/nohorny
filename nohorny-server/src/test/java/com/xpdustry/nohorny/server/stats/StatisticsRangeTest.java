// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import com.xpdustry.nohorny.server.ServerTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class StatisticsRangeTest extends ServerTest {

    @ParameterizedTest
    @CsvSource({"24h, hour, 24", "7d, day, 7", "30d, day, 30", "90d, day, 90"})
    void historyEchoesRange(final String range, final String slot, final int slots) throws Exception {
        this.mvc
                .perform(get("/api/stats/history").param("range", range))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value(range))
                .andExpect(jsonPath("$.slot").value(slot))
                .andExpect(jsonPath("$.slots.length()").value(slots));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1y", "24H"})
    void unknownRangeIsRejected(final String range) throws Exception {
        this.mvc
                .perform(get("/api/stats/history").param("range", range))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("unknown range"));
    }
}
