// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.stats;

import com.xpdustry.nohorny.server.ClassificationRecordedEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/// Pushes the statistics to the connected event streams after each classification, at most once per second.
///
/// It stops before the web server, so the open streams do not hold back its graceful shutdown.
@Component
public final class StatisticsBroadcaster implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StatisticsBroadcaster.class);
    private static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final Duration PUSH_INTERVAL = Duration.ofSeconds(1);
    private static final String EVENT_NAME = "stats";

    private final StatisticsService statistics;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("nohorny-stats").daemon().factory());
    private volatile boolean running = false;
    private volatile long lastPush = 0L;

    public StatisticsBroadcaster(final StatisticsService statistics) {
        this.statistics = statistics;
    }

    public SseEmitter subscribe() throws IOException {
        final var emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError(error -> this.emitters.remove(emitter));
        if (!this.running) {
            emitter.send(this.event());
            emitter.complete();
            return emitter;
        }
        try {
            // Registered then greeted on the push thread: a classification recorded before the registration is in the
            // first snapshot, one recorded after schedules a push that runs behind it
            this.executor.execute(() -> {
                this.emitters.add(emitter);
                try {
                    this.send(emitter, this.event());
                } catch (final RuntimeException exception) {
                    log.error("Failed to send the first statistics", exception);
                    this.emitters.remove(emitter);
                    emitter.completeWithError(exception);
                }
            });
        } catch (final RejectedExecutionException exception) {
            // Stopping, the stream ends right away
            emitter.complete();
        }
        return emitter;
    }

    @EventListener
    public void onClassificationRecorded(final ClassificationRecordedEvent event) {
        if (!this.running || this.emitters.isEmpty() || !this.scheduled.compareAndSet(false, true)) {
            return;
        }
        final var delay = Math.max(0L, this.lastPush + PUSH_INTERVAL.toNanos() - System.nanoTime());
        final var _ = this.executor.schedule(this::push, delay, TimeUnit.NANOSECONDS);
    }

    private void push() {
        // Cleared first, a classification recorded during the push schedules the next one
        this.scheduled.set(false);
        this.lastPush = System.nanoTime();
        try {
            final var event = this.event();
            for (final var emitter : this.emitters) {
                this.send(emitter, event);
            }
        } catch (final RuntimeException exception) {
            log.error("Failed to push the statistics", exception);
        }
    }

    private void heartbeat() {
        final var event = SseEmitter.event().comment("heartbeat");
        for (final var emitter : this.emitters) {
            this.send(emitter, event);
        }
    }

    private void send(final SseEmitter emitter, final SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (final IOException | IllegalStateException exception) {
            // The client is gone, the container completes the emitter
            this.emitters.remove(emitter);
        }
    }

    private SseEmitter.SseEventBuilder event() {
        return SseEmitter.event().name(EVENT_NAME).data(this.statistics.compute(), MediaType.APPLICATION_JSON);
    }

    @Override
    public void start() {
        final var heartbeat = HEARTBEAT_INTERVAL.toMillis();
        final var _ =
                this.executor.scheduleWithFixedDelay(this::heartbeat, heartbeat, heartbeat, TimeUnit.MILLISECONDS);
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
        this.executor.shutdownNow();
        for (final var emitter : this.emitters) {
            emitter.complete();
        }
        this.emitters.clear();
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }
}
