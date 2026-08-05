// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import arc.mock.MockSettings;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ProxiflyProxySelectorTest {

    @BeforeEach
    void before() {
        Core.settings = new MockSettings();
    }

    @AfterEach
    void after() {
        Core.settings = null;
    }

    @EnabledIfEnvironmentVariable(named = "NOHORNY_TEST_PROXIFLY", matches = "true|1")
    @Test
    void select_working_proxy_from_live_proxifly_list() {
        NoHornySetting.PROXY_COUNTRIES.set("DE,NL,US,JP");
        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var selector = new ProxiflyProxySelector(executor);
            final var proxy = selector.refresh(true);
            assertNotNull(proxy, "Expected a Proxifly HTTP proxy capable of reaching Discord's gateway endpoint");
            assertEquals(Proxy.Type.HTTP, proxy.type());
            assertInstanceOf(InetSocketAddress.class, proxy.address());
        }
    }
}
