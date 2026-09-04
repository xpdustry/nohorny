// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.DrawInstruction;
import com.xpdustry.nohorny.common.GraphicsScope;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.NoHornyPreconditions;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public final class MindustryImageRenderer {

    private static final int MAX_IMAGE_SIZE = 2048;
    private static final int BLOCK_SIZE = 32;

    private MindustryImageRenderer() {}

    @SuppressWarnings("unchecked")
    public static BufferedImage render(final VirtualBuilding.Group<? extends MindustryImage> group) {
        final var renderW = NoHornyPreconditions.positive(group.w() * BLOCK_SIZE, "group w");
        final var renderH = NoHornyPreconditions.positive(group.h() * BLOCK_SIZE, "group h");

        final double downscaling =
                Math.min(1D, Math.min(MAX_IMAGE_SIZE / (double) renderW, MAX_IMAGE_SIZE / (double) renderH));
        final int imageW = Math.min(MAX_IMAGE_SIZE, (int) Math.ceil(renderW * downscaling));
        final int imageH = Math.min(MAX_IMAGE_SIZE, (int) Math.ceil(renderH * downscaling));
        final var image = new BufferedImage(imageW, imageH, BufferedImage.TYPE_INT_RGB);

        try (final var global = new GraphicsScope(image)) {
            global.graphics().setColor(Color.BLACK);
            global.graphics().fillRect(0, 0, image.getWidth(), image.getHeight());
            // Invert y-axis, because Mindustry uses bottom-left as the origin
            // The flip also includes downscaling in case the virtual image is too big
            global.graphics().translate(0, imageH);
            global.graphics().scale(downscaling, -downscaling);

            final var tiled = new HashMap<TiledDisplayKey, VirtualBuildingIndex<MindustryDisplay>>();
            for (final var building : group.elements()) {
                if (building.data() instanceof MindustryDisplay display && display.tiled() != null) {
                    final var key = new TiledDisplayKey(
                            building.size(),
                            display.resolution(),
                            display.tiled().frameSize());
                    tiled.computeIfAbsent(key, _ -> new VirtualBuildingIndex<>())
                            .upsert((VirtualBuilding<MindustryDisplay>) building);
                    continue;
                }

                final int x = (building.x() - group.x()) * BLOCK_SIZE;
                final int y = (building.y() - group.y()) * BLOCK_SIZE;
                final int size = building.size() * BLOCK_SIZE;

                try (final var local = global.child(x, y, size, size)) {
                    final var buildingScale = (double) size / building.data().resolution();
                    local.graphics().scale(buildingScale, buildingScale);
                    MindustryImageRenderer.renderTrivialDisplayOrCanvas(local.graphics(), building.data());
                }
            }

            MindustryImageRenderer.renderTiledDisplays(global, group, tiled);
        }

        return image;
    }

    private static void renderTrivialDisplayOrCanvas(final Graphics2D graphics, final MindustryImage data) {
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

    private static void renderTiledDisplays(
            final GraphicsScope global,
            final VirtualBuilding.Group<? extends MindustryImage> parent,
            final Map<TiledDisplayKey, VirtualBuildingIndex<MindustryDisplay>> indexes) {
        for (final var entry : indexes.entrySet()) {
            final var key = entry.getKey();
            final var index = entry.getValue();

            for (final var group : index.selectAllGroups()) {
                final var clip = new Area();
                for (final var building : group.elements()) {
                    clip.add(new Area(new Rectangle2D.Double(
                            (building.x() - parent.x()) * BLOCK_SIZE,
                            (building.y() - parent.y()) * BLOCK_SIZE,
                            building.size() * BLOCK_SIZE,
                            building.size() * BLOCK_SIZE)));
                }

                final var scale = (double) (key.size * BLOCK_SIZE) / key.resolution;
                final var frameSize = key.frameSize * scale;
                final var originX = (group.x() - parent.x()) * BLOCK_SIZE;
                final var originY = (group.y() - parent.y()) * BLOCK_SIZE;
                clip.intersect(new Area(new Rectangle2D.Double(
                        originX + frameSize,
                        originY + frameSize,
                        (group.w() * BLOCK_SIZE) - (frameSize * 2D),
                        (group.h() * BLOCK_SIZE) - (frameSize * 2D))));

                try (final var local = global.child()) {
                    local.graphics().clip(clip);
                    local.graphics().translate(originX + frameSize, originY + frameSize);
                    local.graphics().scale(scale, scale);

                    for (final var building : group.elements()) {
                        MindustryImageRenderer.renderTrivialDisplayOrCanvas(local.graphics(), building.data());
                    }
                }
            }
        }
    }

    private record TiledDisplayKey(int size, int resolution, int frameSize) {}
}
