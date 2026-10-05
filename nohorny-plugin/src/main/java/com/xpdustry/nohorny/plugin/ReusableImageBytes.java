// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.plugin;

import arc.util.Timer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;

// PipedInputStream treats a dead reader thread as a broken pipe. HttpClient can
// resume reading on another thread, so short-lived virtual threads can trigger
// "Read end dead" between reads. Reusing a byte buffer avoids that assumption.
final class ReusableImageBytes extends ByteArrayOutputStream implements LifecycleListener {

    private static final float CLEANUP_INTERVAL_SECONDS = 60F;
    private static final Duration RESET_AFTER_LAST_USES_DELAY = Duration.ofMinutes(5);
    // Above the 0.75 default, which smears the hard edges and flat colours of the renders
    private static final float JPEG_QUALITY = 0.9F;

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

    public HttpRequest.BodyPublisher encodeJpeg(final BufferedImage image) throws IOException {
        this.reset();
        this.lastUsed = System.nanoTime();
        writeJpeg(image, this);
        return HttpRequest.BodyPublishers.ofByteArray(this.buf, 0, this.count);
    }

    /// Writes the image as the JPEG the plugin sends everywhere, leaving the stream open.
    static void writeJpeg(final BufferedImage image, final OutputStream stream) throws IOException {
        final var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No jpeg image writer is available");
        }
        final var writer = writers.next();
        // Buffered in memory, closing it flushes into the stream without closing it
        try (final var output = new MemoryCacheImageOutputStream(stream)) {
            writer.setOutput(output);
            final var param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
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
