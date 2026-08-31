// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import com.xpdustry.nohorny.persistence.ClassificationRequestRepository;
import com.xpdustry.nohorny.persistence.ClassificationRequestSummary;
import com.xpdustry.nohorny.server.MindustryClientDirectory.ClientInfo;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class AdminControllerTest {

    private ClassificationRequestRepository requests;
    private MindustryClientDirectory clients;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        this.requests = mock(ClassificationRequestRepository.class);
        this.clients = mock(MindustryClientDirectory.class);
        when(this.clients.whois(anyString())).thenReturn(new ClientInfo("localhost", "localhost"));
        this.mvc = MockMvcBuilders.standaloneSetup(new AdminController(this.requests, this.clients))
                .build();
    }

    @Test
    void continuesFromLastItemWithoutRepeatingRequests() throws Exception {
        final var firstPage = requestsFrom(100, 80);
        final var secondPage = requestsFrom(80, 79);
        when(this.requests.findAllByOrderByIdDesc(any())).thenReturn(firstPage);
        when(this.requests.findAllByIdLessThanOrderByIdDesc(eq(81L), any())).thenReturn(secondPage);

        this.mvc
                .perform(get("/admin/api/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].id").value(100))
                .andExpect(jsonPath("$.items[19].id").value(81))
                .andExpect(jsonPath("$.nextCursor").value(81));

        this.mvc
                .perform(get("/admin/api/requests").queryParam("before", "81"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(80))
                .andExpect(jsonPath("$.items[1].id").value(79))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    private static List<ClassificationRequestSummary> requestsFrom(final long first, final long last) {
        return LongStream.iterate(first, id -> id >= last, id -> id - 1)
                .mapToObj(AdminControllerTest::request)
                .toList();
    }

    private static ClassificationRequestSummary request(final long id) {
        final var request = mock(ClassificationRequestSummary.class);
        when(request.getId()).thenReturn(id);
        when(request.getCreatedAt()).thenReturn(Instant.EPOCH);
        when(request.getClassifier()).thenReturn("test");
        when(request.isSuccessful()).thenReturn(true);
        when(request.getRemoteAddress()).thenReturn("127.0.0.1");
        when(request.getImageMediaType()).thenReturn("image/png");
        return request;
    }
}
