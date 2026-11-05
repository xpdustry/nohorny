// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Boots the whole server on a throwaway data directory. The subclasses share the same application context.
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ServerTest {

    private static final String ADMIN_PASSWORD = "admin-test-password";

    private static final Path DATA = createDataDirectory();

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("nohorny.storage.database", () -> DATA.resolve("database.sqlite"));
        registry.add("nohorny.storage.images", () -> DATA.resolve("images"));
        registry.add("nohorny.security.admin.password", () -> ADMIN_PASSWORD);
        registry.add("nohorny.classifier.vit.source.type", () -> "local");
        // Downloaded by Gradle
        registry.add(
                "nohorny.classifier.vit.source.local.path",
                () -> Objects.requireNonNull(System.getProperty("nohorny.test.vit-model")));
    }

    /// Signs in through the login form, as the admin page does.
    protected MockHttpSession signIn(final String username, final String password) throws Exception {
        final var csrf =
                this.mvc.perform(get("/api/status")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        final var token = Objects.requireNonNull(csrf).getValue();
        final var login = this.mvc
                .perform(post("/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) Objects.requireNonNull(login.getRequest().getSession(false));
    }

    private static Path createDataDirectory() {
        try {
            final var directory = Files.createTempDirectory("nohorny-server-test");
            directory.toFile().deleteOnExit();
            return directory;
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
