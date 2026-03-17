// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.ApplicationListener;
import arc.Core;
import com.xpdustry.nohorny.common.lifecycle.LifecycleManager;
import mindustry.mod.Plugin;

public final class NoHornyPlugin extends Plugin {

    @Override
    public void init() {
        final var lifecycle = new LifecycleManager();
        lifecycle.addListener(new AutoModerator());
        final var client = new NoHornyClient();
        lifecycle.addListener(client);
        lifecycle.addListener(new DisplayTracker(client));
        lifecycle.addListener(new CanvasTracker(client));
        lifecycle.init();
        Core.app.addListener(new ApplicationListener() {
            @Override
            public void dispose() {
                lifecycle.exit();
            }
        });
    }
}
