// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.authentication;

import com.sun.net.httpserver.HttpExchange;

public final class AllowAllAuthenticator implements Authenticator {

    @Override
    public Identity identify(final HttpExchange exchange) {
        return new Identity("allow-all", exchange.getRemoteAddress().toString());
    }
}
