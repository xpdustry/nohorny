// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.tracking;

import arc.math.geom.Point2;
import arc.struct.IntSet;
import com.xpdustry.nohorny.NoHornyClient;
import com.xpdustry.nohorny.NoHornyListener;
import com.xpdustry.nohorny.geometry.VirtualBuilding;
import com.xpdustry.nohorny.geometry.VirtualBuildingIndex;
import com.xpdustry.nohorny.image.MindustryImage;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

public abstract class BaseTracker<I extends MindustryImage> implements NoHornyListener {

    private final Lock lock = new ReentrantLock();
    private final IntSet marked = new IntSet();
    private @Nullable ScheduledFuture<?> draining;
    private final VirtualBuildingIndex<I> index;
    private final ScheduledExecutorService scheduler;
    private final NoHornyClient client;
    private final BaseTrackerConfig config;

    protected BaseTracker(
            final VirtualBuildingIndex<I> index,
            final ScheduledExecutorService scheduler,
            final NoHornyClient client,
            final BaseTrackerConfig config) {
        this.index = index;
        this.scheduler = scheduler;
        this.client = client;
        this.config = config;
    }

    @Override
    public void onInit() {
        this.draining = this.scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        this.serialExecute(this::drain);
                    } catch (final Exception e) {
                        LoggerFactory.getLogger(this.getClass())
                                .error("An error occurred during scheduled draining", e);
                    }
                },
                this.config.processingDelay().toMillis(),
                this.config.processingDelay().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void drain() {
        final var processing = new ArrayList<VirtualBuilding.Group<I>>();
        for (final var group : this.index.groups()) {
            final var modified = group.elements().stream()
                    .filter(element -> this.marked.contains(Point2.pack(element.x(), element.y())))
                    .count();
            final var eligible = group.elements().stream()
                    .filter(element -> this.isEligible(element.data()))
                    .count();
            if (modified == 0L
                    || eligible < this.config.minimumGroupSize()
                    || (float) modified / group.elements().size() < this.config.processingThreshold()) {
                continue;
            }
            processing.add(group);
            for (final var element : group.elements()) {
                this.unmarkForProcessing(element.x(), element.y());
            }
        }
        this.client.accept(processing);
    }

    protected final void serialExecute(final Runnable runnable) {
        this.scheduler.execute(() -> {
            try {
                this.lock.lockInterruptibly();
            } catch (final InterruptedException e) {
                return;
            }
            try {
                runnable.run();
            } finally {
                this.lock.unlock();
            }
        });
    }

    @Override
    public void onExit() {
        if (this.draining != null) {
            this.draining.cancel(true);
            this.draining = null;
        }
    }

    protected final void markForProcessing(final int x, final int y) {
        this.marked.add(Point2.pack(x, y));
    }

    protected final void unmarkAllForProcessing() {
        this.marked.clear();
    }

    protected final void unmarkForProcessing(final int x, final int y) {
        this.marked.remove(Point2.pack(x, y));
    }

    protected abstract boolean isEligible(final I image);
}
