// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.authentication;

import com.sun.net.httpserver.HttpExchange;
import org.jspecify.annotations.Nullable;

public final class LocalhostAuthenticator implements Authenticator {

    @Override
    public @Nullable Identity identify(final HttpExchange exchange) {
        return exchange.getRemoteAddress().getAddress().isLoopbackAddress()
                ? new Identity("localhost", "localhost")
                : null;
    }
}
