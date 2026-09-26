// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.xpdustry.nohorny.common.DrawInstruction;
import com.xpdustry.nohorny.common.ImmutableByteArray;
import com.xpdustry.nohorny.common.ImmutableIntArray;
import com.xpdustry.nohorny.common.MindustryCanvas;
import com.xpdustry.nohorny.common.MindustryDisplay;
import com.xpdustry.nohorny.common.MindustryImage;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Buildings are 1x1 and have a resolution of 32, so one display unit maps to one pixel.
// Unlike Mindustry, the rendered image origin is the top-left corner.
final class MindustryImageRendererTest {

    private static final int RESOLUTION = 32;
    private static final int FRAME = 6;
    private static final DrawInstruction FILL = new DrawInstruction.DrawRect(-1000, -1000, 2000, 2000);

    @Test
    void regular_display_is_clipped_to_its_building() {
        final var image = render(2, 1, display(0, 0, color(Color.RED), FILL));

        assertPixel(image, 0, 16, Color.RED);
        assertPixel(image, 31, 16, Color.RED);
        assertPixel(image, 32, 16, Color.BLACK);
    }

    @Test
    void draws_rectangles_from_the_bottom_left() {
        final var image = render(1, 1, display(0, 0, color(Color.RED), new DrawInstruction.DrawRect(0, 0, 16, 16)));

        assertPixel(image, 8, 24, Color.RED);
        assertPixel(image, 24, 24, Color.BLACK);
        assertPixel(image, 8, 8, Color.BLACK);
    }

    @Test
    void draws_triangles() {
        final var image =
                render(1, 1, display(0, 0, color(Color.RED), new DrawInstruction.DrawTrig(0, 0, 32, 0, 0, 32)));

        assertPixel(image, 4, 28, Color.RED);
        assertPixel(image, 28, 4, Color.BLACK);
    }

    @Test
    void out_of_range_colors_bleed_into_the_next_channel() {
        final var image = render(1, 1, display(0, 0, new DrawInstruction.SetColor(300, 0, 0, 255), FILL));

        // 300 = 0x12C, the overflowing bit ends up in the green channel, like in game
        assertPixel(image, 16, 16, new Color(0x2C, 0x01, 0x00));
    }

    @Test
    void connected_tiled_displays_share_a_framebuffer() {
        final var image = render(2, 1, tiled(0, 0, FRAME, color(Color.RED), FILL), tiled(1, 0, FRAME));

        assertPixel(image, FRAME - 1, 16, Color.BLACK);
        assertPixel(image, FRAME, 16, Color.RED);
        assertPixel(image, 63 - FRAME, 16, Color.RED);
        assertPixel(image, 64 - FRAME, 16, Color.BLACK);
    }

    @Test
    void tiled_displays_do_not_render_into_missing_tiles() {
        final var image =
                render(2, 2, tiled(0, 0, FRAME, color(Color.RED), FILL), tiled(1, 0, FRAME), tiled(0, 1, FRAME));

        assertPixel(image, 16, 16, Color.RED);
        assertPixel(image, 48, 48, Color.RED);
        assertPixel(image, 16, 48, Color.RED);
        assertPixel(image, 48, 16, Color.BLACK);
    }

    @Test
    void disconnected_tiled_displays_have_separate_framebuffers() {
        final var square = new DrawInstruction.DrawRect(0, 0, 20, 20);
        final var image = render(
                3, 1, tiled(0, 0, FRAME, color(Color.RED), square), tiled(2, 0, FRAME, color(Color.RED), square));

        assertPixel(image, 16, 16, Color.RED);
        assertPixel(image, 48, 16, Color.BLACK);
        assertPixel(image, 80, 16, Color.RED);
    }

    @Test
    void regular_display_splits_tiled_framebuffers() {
        final var image = render(
                3,
                1,
                tiled(0, 0, FRAME, color(Color.RED), FILL),
                display(1, 0, color(Color.GREEN), FILL),
                tiled(2, 0, FRAME, color(Color.BLUE), FILL));

        assertPixel(image, 16, 16, Color.RED);
        assertPixel(image, 32 - FRAME, 16, Color.BLACK);
        assertPixel(image, 32, 16, Color.GREEN);
        assertPixel(image, 63, 16, Color.GREEN);
        assertPixel(image, 64 + FRAME - 1, 16, Color.BLACK);
        assertPixel(image, 80, 16, Color.BLUE);
    }

    @Test
    void tiled_displays_use_their_frame_size() {
        final var image = render(1, 1, tiled(0, 0, 2, color(Color.RED), FILL));

        assertPixel(image, 1, 16, Color.BLACK);
        assertPixel(image, 2, 16, Color.RED);
        assertPixel(image, 29, 16, Color.RED);
        assertPixel(image, 30, 16, Color.BLACK);
    }

    @Test
    void tiled_displays_with_different_frame_sizes_have_separate_framebuffers() {
        final var image = render(2, 1, tiled(0, 0, FRAME, color(Color.RED), FILL), tiled(1, 0, 2));

        assertPixel(image, 16, 16, Color.RED);
        assertPixel(image, 48, 16, Color.BLACK);
    }

    @Test
    void renders_the_whole_framebuffer_of_oversized_tiled_displays() {
        // The game hides framebuffers that are too large, but moderation must still inspect what the player drew
        final var displays = IntStream.range(0, 17)
                .mapToObj(x -> x == 0 ? tiled(x, 0, FRAME, color(Color.RED), FILL) : tiled(x, 0, FRAME))
                .toList();
        final var image = render(17, 1, displays);

        assertPixel(image, FRAME, 16, Color.RED);
        assertPixel(image, (17 * 32) - FRAME - 1, 16, Color.RED);
    }

    @Test
    void renders_canvases_from_the_top_left() {
        final var palette = ImmutableIntArray.wrap(0xFF0000FF, 0x00FF00FF);
        final var pixels = ImmutableByteArray.wrap((byte) 0, (byte) 1, (byte) 1, (byte) 1);
        final var image = render(1, 1, building(0, 0, new MindustryCanvas(2, palette, pixels, null)));

        assertPixel(image, 8, 8, Color.RED);
        assertPixel(image, 24, 8, Color.GREEN);
        assertPixel(image, 8, 24, Color.GREEN);
        assertPixel(image, 24, 24, Color.GREEN);
    }

    @Test
    void downscales_groups_larger_than_the_maximum_image_size() {
        final var image = render(128, 1, display(0, 0, color(Color.RED), FILL));

        assertEquals(2048, image.getWidth());
        assertEquals(16, image.getHeight());
        assertPixel(image, 8, 8, Color.RED);
        assertPixel(image, 24, 8, Color.BLACK);
    }

    private static DrawInstruction color(final Color color) {
        return new DrawInstruction.SetColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private static VirtualBuilding<MindustryImage> display(
            final int x, final int y, final DrawInstruction... instructions) {
        return display(x, y, null, instructions);
    }

    private static VirtualBuilding<MindustryImage> tiled(
            final int x, final int y, final int frameSize, final DrawInstruction... instructions) {
        return display(x, y, new MindustryDisplay.Tiled(frameSize), instructions);
    }

    private static VirtualBuilding<MindustryImage> display(
            final int x,
            final int y,
            final MindustryDisplay.@Nullable Tiled tiled,
            final DrawInstruction... instructions) {
        final var processor = new MindustryDisplay.Processor(List.of(instructions), null);
        return building(x, y, new MindustryDisplay(RESOLUTION, Map.of(0, processor), tiled));
    }

    private static VirtualBuilding<MindustryImage> building(final int x, final int y, final MindustryImage image) {
        return new VirtualBuilding<>(x, y, 1, image);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static BufferedImage render(final int w, final int h, final VirtualBuilding<MindustryImage>... buildings) {
        return render(w, h, List.of(buildings));
    }

    private static BufferedImage render(
            final int w, final int h, final List<VirtualBuilding<MindustryImage>> buildings) {
        return MindustryImageRenderer.render(new VirtualBuilding.Group<>(0, 0, w, h, buildings));
    }

    private static void assertPixel(final BufferedImage image, final int x, final int y, final Color expected) {
        assertEquals(expected.getRGB(), image.getRGB(x, y), "pixel at (" + x + ", " + y + ")");
    }
}
