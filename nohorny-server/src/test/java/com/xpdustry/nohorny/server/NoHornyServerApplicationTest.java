// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class NoHornyServerApplicationTest extends ServerTest {

    // Also validates the repository queries, which only fail when the context starts
    @Test
    void startsAndServesStatus() throws Exception {
        this.mvc.perform(get("/api/status")).andExpect(status().isOk());
    }
}
