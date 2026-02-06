// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.authentication;

import com.sun.net.httpserver.HttpExchange;
import com.xpdustry.nohorny.NoHornyListener;
import org.jspecify.annotations.Nullable;

public interface Authenticator extends NoHornyListener {

    @Nullable Identity identify(final HttpExchange exchange);

    record Identity(String type, String name) {
        public Identity {
            if (type.isBlank()) {
                throw new IllegalArgumentException("type must not be blank");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }
}
