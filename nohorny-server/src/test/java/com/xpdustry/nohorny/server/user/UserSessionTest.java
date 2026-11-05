// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.user;

import com.xpdustry.nohorny.server.ServerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class UserSessionTest extends ServerTest {

    @Autowired
    private UserService users;

    @Test
    void passwordChangeEndsSessions() throws Exception {
        this.users.create("password-change", "first-password", false, null);
        final var session = this.signIn("password-change", "first-password");
        this.mvc.perform(get("/api/session").session(session)).andExpect(status().isOk());

        this.users.changePassword("password-change", "second-password");

        this.mvc.perform(get("/api/session").session(session)).andExpect(status().isUnauthorized());
        this.signIn("password-change", "second-password");
    }

    @Test
    void roleChangeEndsSessions() throws Exception {
        this.users.create("role-change", "role-password", false, null);
        final var session = this.signIn("role-change", "role-password");

        this.users.changeRole("role-change", true, "admin");

        this.mvc.perform(get("/api/session").session(session)).andExpect(status().isUnauthorized());
    }
}
