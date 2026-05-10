// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class MindustryImageRenderer {

    private static final int MAX_IMAGE_SIZE = 2048;
    private static final int BLOCK_SIZE = 32;

    private MindustryImageRenderer() {}

    public static BufferedImage render(final VirtualBuilding.Group<? extends MindustryImage> group) {
        final long renderW = NoHornyPreconditions.positive((long) group.w() * BLOCK_SIZE, "group w");
        final long renderH = NoHornyPreconditions.positive((long) group.h() * BLOCK_SIZE, "group h");

        final double downscaling =
                Math.min(1D, Math.min(MAX_IMAGE_SIZE / (double) renderW, MAX_IMAGE_SIZE / (double) renderH));
        final int imageW = Math.min(MAX_IMAGE_SIZE, (int) Math.ceil(renderW * downscaling));
        final int imageH = Math.min(MAX_IMAGE_SIZE, (int) Math.ceil(renderH * downscaling));
        final var image = new BufferedImage(imageW, imageH, BufferedImage.TYPE_INT_ARGB);

        try (final var scope = new GraphicsScope(image)) {
            scope.graphics().setColor(Color.BLACK);
            scope.graphics().fillRect(0, 0, image.getWidth(), image.getHeight());
            // Invert y-axis, because Mindustry uses bottom-left as the origin
            // The flip also includes downscaling in case the virtual image is too big
            scope.graphics().translate(0, imageH);
            scope.graphics().scale(downscaling, -downscaling);

            for (final var building : group.elements()) {
                final int x = (building.x() - group.x()) * BLOCK_SIZE;
                final int y = (building.y() - group.y()) * BLOCK_SIZE;
                final int size = building.size() * BLOCK_SIZE;
                try (final var childScope =
                        new GraphicsScope((Graphics2D) scope.graphics().create(x, y, size, size))) {
                    final var childScale = (double) size / building.data().resolution();
                    childScope.graphics().scale(childScale, childScale);
                    MindustryImageRenderer.renderWith(childScope.graphics(), building.data());
                }
            }
        }
        return image;
    }

    private static void renderWith(final Graphics2D graphics, final MindustryImage data) {
        switch (data) {
            case MindustryDisplay display -> {
                final var xa = new int[3];
                final var ya = new int[3];
                for (final var processor : display.processors().values()) {
                    for (final var instruction : processor.instructions()) {
                        switch (instruction) {
                            case DrawInstruction.SetColor(int r, int g, int b, int a) ->
                                graphics.setColor(new Color(r, g, b, a));
                            case DrawInstruction.DrawRect(int x, int y, int w, int h) -> graphics.fillRect(x, y, w, h);
                            case DrawInstruction.DrawTrig(int x1, int y1, int x2, int y2, int x3, int y3) -> {
                                xa[0] = x1;
                                xa[1] = x2;
                                xa[2] = x3;
                                ya[0] = y1;
                                ya[1] = y2;
                                ya[2] = y3;
                                graphics.fillPolygon(xa, ya, 3);
                            }
                        }
                    }
                }
            }
            case MindustryCanvas canvas -> {
                for (int y = 0; y < canvas.resolution(); y++) {
                    for (int x = 0; x < canvas.resolution(); x++) {
                        final var index = Byte.toUnsignedInt(canvas.pixels().get(x + (y * canvas.resolution())));
                        final var value = canvas.palette().get(index) >>> 8;
                        // The constructor of java.awt.Color is (int rgba),
                        // but actually, it expects argb, what the fuck java...
                        final var color = new Color(value, false);
                        graphics.setColor(color);
                        // pixmap origin is top-left, so we cancel the inverting of the Y axis here.
                        graphics.fillRect(x, canvas.resolution() - y - 1, 1, 1);
                    }
                }
            }
        }
    }
}
