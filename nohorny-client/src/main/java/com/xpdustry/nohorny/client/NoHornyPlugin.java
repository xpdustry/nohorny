// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.ApplicationListener;
import arc.Core;
import java.util.ArrayList;
import java.util.List;
import mindustry.Vars;
import mindustry.mod.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoHornyPlugin extends Plugin {

    private static final Logger logger = LoggerFactory.getLogger(NoHornyPlugin.class);
    private final List<LifecycleListener> listeners = new ArrayList<>();

    @Override
    public void init() {
        final var directory = Vars.mods.getConfigFolder(this).file().toPath();

        final var client = new NoHornyClient();
        this.addListener(client);

        final var displays = new DisplayTracker(client);
        this.addListener(displays);

        final var canvases = new CanvasTracker(client);
        this.addListener(canvases);

        final var debug = new DebugHelper(directory.resolve("debug"), canvases, displays);
        this.addListener(debug);

        this.addListener(new AutoModerator());

        this.init0();
        Core.app.addListener(new ApplicationListener() {
            @Override
            public void dispose() {
                NoHornyPlugin.this.exit0();
            }
        });
    }

    private void addListener(final LifecycleListener listener) {
        this.listeners.add(listener);
    }

    private void init0() {
        for (int i = 0; i < this.listeners.size(); i++) {
            try {
                this.listeners.get(i).onInit();
            } catch (final Exception e1) {
                for (; i > 0; --i) {
                    try {
                        this.listeners.get(i).onExit();
                    } catch (final Exception e2) {
                        e1.addSuppressed(e2);
                    }
                }
                throw new RuntimeException("Failed to initialize NoHorny", e1);
            }
        }
        logger.info("NoHorny successfully initialized");
    }

    private void exit0() {
        for (final var listener : this.listeners.reversed()) {
            try {
                listener.onExit();
            } catch (final Throwable e) {
                logger.error(
                        "NoHorny failed to exit {} gracefully",
                        listener.getClass().getSimpleName(),
                        e);
            }
        }
    }
}
