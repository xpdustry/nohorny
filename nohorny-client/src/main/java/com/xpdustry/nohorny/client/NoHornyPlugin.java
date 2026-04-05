// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.ApplicationListener;
import arc.Core;
import com.xpdustry.nohorny.common.lifecycle.LifecycleManager;
import mindustry.Vars;
import mindustry.mod.Plugin;

public final class NoHornyPlugin extends Plugin {

    @Override
    public void init() {
        final var directory = Vars.mods.getConfigFolder(this).file().toPath();

        final var lifecycle = new LifecycleManager();
        lifecycle.addListener(new AutoModerator());
        final var client = new NoHornyClient();
        lifecycle.addListener(client);
        final var canvases = new CanvasTracker(client);
        lifecycle.addListener(canvases);
        final var displays = new DisplayTracker(client);
        lifecycle.addListener(displays);
        final var debug = new DebugHelper(directory.resolve("debug"), canvases, displays);
        lifecycle.addListener(debug);

        lifecycle.init();
        Core.app.addListener(new ApplicationListener() {
            @Override
            public void dispose() {
                lifecycle.exit();
            }
        });
    }
}
