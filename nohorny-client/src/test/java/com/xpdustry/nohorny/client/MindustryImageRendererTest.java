// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.DrawInstruction;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MindustryImageRendererTest {

    @Test
    void bounds_regular_displays_to_their_building() {
        final var image =
                render(2, 1, List.of(regularDisplay(0, 0, new DrawInstruction.DrawRect(-100, -100, 300, 300))));

        assertPixel(Color.RED, image, 16, 16);
        assertPixel(Color.BLACK, image, 48, 16);
    }

    @Test
    void renders_connected_tiled_displays_as_one_framebuffer() {
        final var image = render(
                2,
                1,
                List.of(tiledDisplay(0, 0, new DrawInstruction.DrawRect(-100, -100, 300, 300)), tiledDisplay(1, 0)));

        assertPixel(Color.BLACK, image, 5, 16);
        assertPixel(Color.RED, image, 6, 16);
        assertPixel(Color.RED, image, 31, 16);
        assertPixel(Color.RED, image, 32, 16);
        assertPixel(Color.RED, image, 57, 16);
        assertPixel(Color.BLACK, image, 58, 16);
    }

    @Test
    void gives_disconnected_tiled_displays_separate_framebuffers() {
        final var image = render(
                3,
                1,
                List.of(
                        tiledDisplay(0, 0, new DrawInstruction.DrawRect(0, 0, 20, 20)),
                        tiledDisplay(2, 0, new DrawInstruction.DrawRect(0, 0, 20, 20))));

        assertPixel(Color.RED, image, 16, 16);
        assertPixel(Color.BLACK, image, 48, 16);
        assertPixel(Color.RED, image, 80, 16);
    }

    @Test
    void regular_display_separates_tiled_framebuffers() {
        final var image = render(
                3,
                1,
                List.of(
                        tiledDisplay(0, 0, Color.RED, new DrawInstruction.DrawRect(0, 0, 20, 20)),
                        regularDisplay(1, 0, Color.GREEN, new DrawInstruction.DrawRect(0, 0, 32, 32)),
                        tiledDisplay(2, 0, Color.BLUE, new DrawInstruction.DrawRect(0, 0, 20, 20))));

        assertPixel(Color.RED, image, 16, 16);
        assertPixel(Color.BLACK, image, 31, 16);
        assertPixel(Color.GREEN, image, 32, 16);
        assertPixel(Color.GREEN, image, 63, 16);
        assertPixel(Color.BLACK, image, 64, 16);
        assertPixel(Color.BLUE, image, 80, 16);
    }

    @Test
    void does_not_render_into_missing_tiles() {
        final var image = render(
                2,
                2,
                List.of(
                        tiledDisplay(0, 0, new DrawInstruction.DrawRect(-100, -100, 300, 300)),
                        tiledDisplay(1, 0),
                        tiledDisplay(0, 1)));

        assertPixel(Color.RED, image, 16, 16);
        assertPixel(Color.BLACK, image, 48, 16);
        assertPixel(Color.RED, image, 16, 48);
        assertPixel(Color.RED, image, 48, 48);
    }

    @Test
    void uses_the_display_frame_size() {
        final var image =
                render(1, 1, List.of(tiledDisplay(0, 0, 2, new DrawInstruction.DrawRect(-100, -100, 300, 300))));

        assertPixel(Color.BLACK, image, 1, 16);
        assertPixel(Color.RED, image, 2, 16);
        assertPixel(Color.RED, image, 29, 16);
        assertPixel(Color.BLACK, image, 30, 16);
    }

    @Test
    void different_frame_sizes_do_not_share_a_framebuffer() {
        final var image = render(
                2,
                1,
                List.of(
                        tiledDisplay(0, 0, 6, new DrawInstruction.DrawRect(-100, -100, 300, 300)),
                        tiledDisplay(1, 0, 2)));

        assertPixel(Color.RED, image, 16, 16);
        assertPixel(Color.BLACK, image, 48, 16);
    }

    @Test
    void renders_oversized_displays_for_moderation() {
        final var displays = new ArrayList<VirtualBuilding<MindustryImage>>();
        displays.add(tiledDisplay(0, 0, new DrawInstruction.DrawRect(-100, -100, 1000, 300)));
        for (int x = 1; x < 17; x++) {
            displays.add(tiledDisplay(x, 0));
        }

        // The game hides this framebuffer, but moderation must still inspect what the player drew.
        final var image = render(17, 1, displays);

        assertPixel(Color.BLACK, image, 5, 16);
        assertPixel(Color.RED, image, 6, 16);
        assertPixel(Color.RED, image, 537, 16);
        assertPixel(Color.BLACK, image, 538, 16);
    }

    private static VirtualBuilding<MindustryImage> display(
            final int x,
            final int y,
            final MindustryDisplay.@Nullable Tiled tiled,
            final Color color,
            final DrawInstruction... instructions) {
        final var allInstructions = new ArrayList<DrawInstruction>(instructions.length + 1);
        allInstructions.add(
                new DrawInstruction.SetColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()));
        allInstructions.addAll(List.of(instructions));
        final var data =
                new MindustryDisplay(32, Map.of(0, new MindustryDisplay.Processor(allInstructions, null)), tiled);
        return new VirtualBuilding<>(x, y, 1, data);
    }

    private static VirtualBuilding<MindustryImage> regularDisplay(
            final int x, final int y, final DrawInstruction... instructions) {
        return regularDisplay(x, y, Color.RED, instructions);
    }

    private static VirtualBuilding<MindustryImage> regularDisplay(
            final int x, final int y, final Color color, final DrawInstruction... instructions) {
        return display(x, y, null, color, instructions);
    }

    private static VirtualBuilding<MindustryImage> tiledDisplay(
            final int x, final int y, final DrawInstruction... instructions) {
        return tiledDisplay(x, y, 6, Color.RED, instructions);
    }

    private static VirtualBuilding<MindustryImage> tiledDisplay(
            final int x, final int y, final Color color, final DrawInstruction... instructions) {
        return tiledDisplay(x, y, 6, color, instructions);
    }

    private static VirtualBuilding<MindustryImage> tiledDisplay(
            final int x, final int y, final int frameSize, final DrawInstruction... instructions) {
        return tiledDisplay(x, y, frameSize, Color.RED, instructions);
    }

    private static VirtualBuilding<MindustryImage> tiledDisplay(
            final int x, final int y, final int frameSize, final Color color, final DrawInstruction... instructions) {
        return display(x, y, new MindustryDisplay.Tiled(frameSize), color, instructions);
    }

    private static BufferedImage render(
            final int width, final int height, final List<VirtualBuilding<MindustryImage>> displays) {
        return MindustryImageRenderer.render(new VirtualBuilding.Group<>(0, 0, width, height, displays));
    }

    private static void assertPixel(final Color expected, final BufferedImage image, final int x, final int y) {
        assertEquals(expected.getRGB(), image.getRGB(x, y), "pixel at (" + x + ", " + y + ")");
    }
}
