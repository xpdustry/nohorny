// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.image;

import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class ImageRenderer {

    private static final int BLOCK_SIZE = 32;

    private ImageRenderer() {}

    public static BufferedImage render(final VirtualBuilding.Group<? extends MindustryImage> group) {
        final var image =
                new BufferedImage(group.w() * BLOCK_SIZE, group.h() * BLOCK_SIZE, BufferedImage.TYPE_INT_ARGB);
        try (final var scope = new GraphicsScope(image)) {
            scope.graphics().setColor(Color.BLACK);
            scope.graphics().fillRect(0, 0, image.getWidth(), image.getHeight());

            // Invert y-axis, because Mindustry uses bottom-left as origin
            scope.graphics().translate(0, image.getHeight());
            scope.graphics().scale(1, -1);

            for (final var building : group.elements()) {
                final int x = (building.x() - group.x()) * BLOCK_SIZE;
                final int y = (building.y() - group.y()) * BLOCK_SIZE;
                final int size = building.size() * BLOCK_SIZE;
                try (final var sub =
                        new GraphicsScope((Graphics2D) scope.graphics().create(x, y, size, size))) {
                    final var scale = (double) size / building.data().resolution();
                    sub.graphics().scale(scale, scale);
                    ImageRenderer.renderWith(sub.graphics(), building.data());
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
                        final var value = canvas.palette().get(index);
                        final var color = new Color(value, true);
                        graphics.setColor(color);
                        graphics.fillRect(x, y, 1, 1);
                    }
                }
            }
        }
    }
}
