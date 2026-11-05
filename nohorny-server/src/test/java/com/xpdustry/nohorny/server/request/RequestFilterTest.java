// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.request;

import com.xpdustry.nohorny.common.Rating;
import com.xpdustry.nohorny.server.ServerTest;
import com.xpdustry.nohorny.server.persistence.ClassificationRequest;
import com.xpdustry.nohorny.server.persistence.ImageState;
import com.xpdustry.nohorny.server.persistence.ImageStore;
import com.xpdustry.nohorny.server.persistence.Requester;
import com.xpdustry.nohorny.server.persistence.UuidV7;
import com.xpdustry.nohorny.server.persistence.Verdict;
import com.xpdustry.nohorny.server.user.UserService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class RequestFilterTest extends ServerTest {

    private static final String NETWORK = "filter-test-network";
    private static final String VERSION = "filter-test-1";

    @Autowired
    private RequestService requests;

    @Autowired
    private UserService users;

    private MockHttpSession admin;
    private String namedWithVersion;
    private String unnamedWithVersion;
    private String namedWithoutVersion;
    private String anonymousWithVersion;

    @BeforeAll
    void seed() throws Exception {
        this.namedWithVersion = this.record(Requester.mindustryNetwork(NETWORK), VERSION);
        this.unnamedWithVersion = this.record(Requester.mindustryNetwork(null), VERSION);
        this.namedWithoutVersion = this.record(Requester.mindustryNetwork(NETWORK), null);
        this.anonymousWithVersion = this.record(Requester.anonymous(), VERSION);
        this.users.create("filter-admin", "filter-password", true, null);
        this.admin = this.signIn("filter-admin", "filter-password");
    }

    @Test
    void version() throws Exception {
        this.list("version", VERSION)
                .andExpect(ids(this.namedWithVersion, this.unnamedWithVersion, this.anonymousWithVersion));
        this.list("requester", "mindustry-network", "noVersion", "true", "network", NETWORK)
                .andExpect(ids(this.namedWithoutVersion));
    }

    @Test
    void network() throws Exception {
        this.list("requester", "mindustry-network", "version", VERSION)
                .andExpect(ids(this.namedWithVersion, this.unnamedWithVersion));
        this.list("requester", "mindustry-network", "version", VERSION, "noNetwork", "true")
                .andExpect(ids(this.unnamedWithVersion));
        this.list("requester", "mindustry-network", "version", VERSION, "network", NETWORK)
                .andExpect(ids(this.namedWithVersion));
        this.list("requester", "mindustry-network", "network", NETWORK)
                .andExpect(ids(this.namedWithVersion, this.namedWithoutVersion));
    }

    @Test
    void contradictoryFilters() throws Exception {
        this.list("version", VERSION, "noVersion", "true").andExpect(status().isBadRequest());
        this.list("requester", "mindustry-network", "network", NETWORK, "noNetwork", "true")
                .andExpect(status().isBadRequest());
        this.list("network", NETWORK).andExpect(status().isBadRequest());
    }

    @Test
    void networks() throws Exception {
        this.mvc
                .perform(get("/api/stats/networks").param("range", "24h").session(this.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unattributed").value(0))
                .andExpect(jsonPath("$.networks[?(@.name=='" + NETWORK + "')].count", contains(2)))
                .andExpect(
                        jsonPath("$.requesters[?(@.type=='anonymous')].count").isNotEmpty());
    }

    private ResultActions list(final String... parameters) throws Exception {
        final var request = get("/api/requests").session(this.admin);
        for (int i = 0; i < parameters.length; i += 2) {
            request.param(parameters[i], parameters[i + 1]);
        }
        return this.mvc.perform(request);
    }

    private static ResultMatcher ids(final String... ids) {
        return jsonPath("$.items[*].id", containsInAnyOrder(List.of(ids).toArray()));
    }

    private String record(final Requester requester, final @Nullable String version) {
        final var id = UuidV7.next().toString();
        final var image = id.getBytes(StandardCharsets.UTF_8);
        this.requests.record(
                new ClassificationRequest(
                        id,
                        Instant.now(),
                        1,
                        "test",
                        new Verdict(Rating.SAFE, 1),
                        version,
                        null,
                        requester,
                        false,
                        "127.0.0.1",
                        ImageState.STORED,
                        ImageStore.hash(image),
                        List.of()),
                image);
        return id;
    }
}
