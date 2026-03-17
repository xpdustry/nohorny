// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.lifecycle;

import java.util.ArrayList;
import java.util.List;

public final class LifecycleManager {

    private final System.Logger logger = System.getLogger(LifecycleManager.class.getName());
    private final List<LifecycleListener> listeners = new ArrayList<>();

    public void addListener(final LifecycleListener listener) {
        this.listeners.add(listener);
    }

    public void init() {
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
        this.logger.log(System.Logger.Level.INFO, "Successfully initialized NoHorny");
    }

    public void exit() {
        for (final var listener : this.listeners.reversed()) {
            try {
                listener.onExit();
            } catch (final Throwable e) {
                this.logger.log(
                        System.Logger.Level.ERROR,
                        "Failed to exit {}",
                        listener.getClass().getSimpleName(),
                        e);
            }
        }
    }
}
