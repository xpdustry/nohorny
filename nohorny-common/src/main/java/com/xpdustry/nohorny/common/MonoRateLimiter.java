// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.time.Duration;
import java.time.Instant;

public final class MonoRateLimiter {

    private final Object lock = new Object();
    private final Duration interval;
    private Instant nextAt = Instant.MIN;

    public MonoRateLimiter(final Duration interval) {
        this.interval = interval;
    }

    public void waitIfRateLimited() throws InterruptedException {
        final var now = Instant.now();
        final Instant sendAt;
        synchronized (this.lock) {
            sendAt = now.isAfter(this.nextAt) ? now : this.nextAt;
            this.nextAt = sendAt.plus(interval);
        }
        final var timeToWait = Duration.between(now, sendAt);
        if (timeToWait.isPositive()) {
            Thread.sleep(timeToWait.toMillis());
        }
    }
}
