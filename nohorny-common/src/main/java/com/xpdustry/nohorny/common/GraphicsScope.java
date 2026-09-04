// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public record GraphicsScope(Graphics2D graphics) implements AutoCloseable {

    public GraphicsScope(final BufferedImage image) {
        this(image.createGraphics());
    }

    public GraphicsScope child() {
        return new GraphicsScope((Graphics2D) this.graphics.create());
    }

    public GraphicsScope child(final int x, final int y, final int w, final int h) {
        return new GraphicsScope((Graphics2D) this.graphics.create(x, y, w, h));
    }

    @Override
    public void close() {
        this.graphics.dispose();
    }
}
