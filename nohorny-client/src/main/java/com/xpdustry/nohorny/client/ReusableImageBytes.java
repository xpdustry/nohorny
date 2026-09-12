// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.util.Timer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;

// PipedInputStream treats a dead reader thread as a broken pipe. HttpClient can
// resume reading on another thread, so short-lived virtual threads can trigger
// "Read end dead" between reads. Reusing a byte buffer avoids that assumption.
final class ReusableImageBytes extends ByteArrayOutputStream implements LifecycleListener {

    private static final float CLEANUP_INTERVAL_SECONDS = 60F;
    private static final Duration RESET_AFTER_LAST_USES_DELAY = Duration.ofMinutes(5);

    private long lastUsed = System.nanoTime();
    private final ReentrantLock lock = new ReentrantLock();
    private final Timer.Task cleanup = new Timer.Task() {
        @Override
        public void run() {
            ReusableImageBytes.this.releaseIfIdle();
        }
    };

    public ReusableImageBytes() {
        super(0);
    }

    public void lock() {
        this.lock.lock();
    }

    public void release() {
        this.lock.unlock();
    }

    public HttpRequest.BodyPublisher encode(final BufferedImage image, final String format) throws IOException {
        this.reset();
        this.lastUsed = System.nanoTime();
        if (!ImageIO.write(image, format, this)) {
            throw new IOException("No " + format + " image writer is available");
        }
        return HttpRequest.BodyPublishers.ofByteArray(this.buf, 0, this.count);
    }

    private void releaseIfIdle() {
        if (!this.lock.tryLock()) {
            return;
        }
        try {
            if (System.nanoTime() - this.lastUsed >= RESET_AFTER_LAST_USES_DELAY.toNanos()) {
                this.buf = new byte[0];
                this.reset();
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void onInit() {
        Timer.schedule(this.cleanup, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS);
    }

    @Override
    public void onExit() {
        this.cleanup.cancel();
        this.lock.lock();
        try {
            this.buf = new byte[0];
            this.reset();
        } finally {
            this.lock.unlock();
        }
    }
}
