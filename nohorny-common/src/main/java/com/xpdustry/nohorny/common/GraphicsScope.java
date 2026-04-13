// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public record GraphicsScope(Graphics2D graphics) implements AutoCloseable {

    public GraphicsScope(final BufferedImage image) {
        this(image.createGraphics());
    }

    @Override
    public void close() {
        this.graphics.dispose();
    }
}
