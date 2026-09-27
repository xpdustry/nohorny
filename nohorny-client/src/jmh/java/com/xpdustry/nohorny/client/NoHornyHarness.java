// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.util.List;

// Installs the trackers, with a classifier that instantly accepts every group, like an infinitely fast server.
// It is the worst case for the main thread, since the trackers never wait and keep grouping.
final class NoHornyHarness implements AutoCloseable {

    private final NoHornyEventBus events = new NoHornyEventBus();
    private final List<LifecycleListener> listeners;
    long groups = 0;
    long grouped = 0;

    NoHornyHarness() {
        final GroupClassifier classifier = group -> {
            this.groups++;
            this.grouped += group.elements().size();
            return true;
        };
        this.listeners = List.of(
                new DisplayTracker(this.events, classifier),
                new CanvasTracker(this.events, classifier),
                PixelTracker.sorters(this.events, classifier),
                PixelTracker.illuminators(this.events, classifier));
        for (final var listener : this.listeners) {
            listener.onInit();
        }
    }

    // Same as the plugin, minus the error handling
    void tick() {
        for (final var listener : this.listeners) {
            listener.onTick();
        }
    }

    @Override
    public void close() {
        this.events.close();
    }
}
