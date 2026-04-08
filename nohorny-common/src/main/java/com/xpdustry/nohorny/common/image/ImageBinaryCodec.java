// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common.image;

import com.xpdustry.nohorny.common.NoHornyChecks;
import com.xpdustry.nohorny.common.geometry.ImmutablePoint2;
import com.xpdustry.nohorny.common.geometry.VirtualBuilding;
import com.xpdustry.nohorny.common.struct.ImmutableByteArray;
import com.xpdustry.nohorny.common.struct.ImmutableIntArray;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public final class ImageBinaryCodec {

    public static final String MEDIA_TYPE = "application/vnd.nohorny.image";

    static final int MAXIMUM_GROUP_ELEMENT_COUNT = 30 * 30;
    static final int MAXIMUM_INSTRUCTION_COUNT = 1000;
    static final int MAXIMUM_PROCESSOR_COUNT = 500;

    private static final byte IMAGE_TYPE_CANVAS = 0;
    private static final byte IMAGE_TYPE_DISPLAY = 1;

    private static final int INSTRUCTION_TYPE_BITS = 2;
    private static final int INSTRUCTION_SET_COLOR = 0;
    private static final int INSTRUCTION_DRAW_RECT = 1;
    private static final int INSTRUCTION_DRAW_TRIG = 2;

    private ImageBinaryCodec() {}

    public static <T extends MindustryImage> void encode(
            final OutputStream output, final VirtualBuilding.Group<T> group) throws IOException {
        final var out = new DataOutputStream(output);
        NoHornyChecks.within(group.elements().size(), 1, MAXIMUM_GROUP_ELEMENT_COUNT, "group element count");
        out.writeInt(group.elements().size());
        for (final var element : group.elements()) {
            out.writeInt(element.x());
            out.writeInt(element.y());
            out.writeInt(element.size());
            ImageBinaryCodec.encodeImage(out, element.data());
        }
        out.flush();
    }

    private static void encodeImage(final DataOutputStream out, final MindustryImage image) throws IOException {
        switch (image) {
            case MindustryCanvas canvas -> {
                out.writeByte(IMAGE_TYPE_CANVAS);
                out.writeInt(canvas.resolution());

                out.writeInt(canvas.palette().length());
                for (int i = 0; i < canvas.palette().length(); i++) {
                    out.writeInt(canvas.palette().get(i));
                }

                out.writeInt(canvas.pixels().length());
                for (int i = 0; i < canvas.pixels().length(); i++) {
                    out.writeByte(canvas.pixels().get(i));
                }
            }
            case MindustryDisplay display -> {
                out.writeByte(IMAGE_TYPE_DISPLAY);
                out.writeInt(display.resolution());

                // TODO Raise min to 1 when implementing iterative grouping with filters
                NoHornyChecks.within(display.processors().size(), 0, MAXIMUM_PROCESSOR_COUNT, "processor count");
                out.writeInt(display.processors().size());
                for (final var entry : display.processors().entrySet()) {
                    final var processor = entry.getValue();
                    final var position = entry.getKey();

                    out.writeShort(position.x());
                    out.writeShort(position.y());

                    NoHornyChecks.within(
                            processor.instructions().size(), 1, MAXIMUM_INSTRUCTION_COUNT, "instruction count");
                    out.writeInt(processor.instructions().size());
                    for (final var instruction : processor.instructions()) {
                        out.writeLong(packInstruction(instruction));
                    }
                }
            }
        }
    }

    private static long packInstruction(final DrawInstruction instruction) {
        return switch (instruction) {
            case DrawInstruction.SetColor(int r, int g, int b, int a) ->
                ImageBinaryCodec.packInstructionType(INSTRUCTION_SET_COLOR)
                        | ImageBinaryCodec.packRGBComponent(r, 0)
                        | ImageBinaryCodec.packRGBComponent(g, 1)
                        | ImageBinaryCodec.packRGBComponent(b, 2)
                        | ImageBinaryCodec.packRGBComponent(a, 3);
            case DrawInstruction.DrawRect(int x, int y, int w, int h) ->
                ImageBinaryCodec.packInstructionType(INSTRUCTION_DRAW_RECT)
                        | ImageBinaryCodec.packingCoordinate(x, 0)
                        | ImageBinaryCodec.packingCoordinate(y, 1)
                        | ImageBinaryCodec.packingCoordinate(w, 2)
                        | ImageBinaryCodec.packingCoordinate(h, 3);
            case DrawInstruction.DrawTrig(int x1, int y1, int x2, int y2, int x3, int y3) ->
                ImageBinaryCodec.packInstructionType(INSTRUCTION_DRAW_TRIG)
                        | ImageBinaryCodec.packingCoordinate(x1, 0)
                        | ImageBinaryCodec.packingCoordinate(y1, 1)
                        | ImageBinaryCodec.packingCoordinate(x2, 2)
                        | ImageBinaryCodec.packingCoordinate(y2, 3)
                        | ImageBinaryCodec.packingCoordinate(x3, 4)
                        | ImageBinaryCodec.packingCoordinate(y3, 5);
        };
    }

    private static long packInstructionType(final int type) {
        return ImageBinaryCodec.pack(type, INSTRUCTION_TYPE_BITS, 0, 0);
    }

    private static long packingCoordinate(final int value, final int index) {
        return ImageBinaryCodec.pack(Math.clamp(value, 0, 511), 9, index, INSTRUCTION_TYPE_BITS);
    }

    private static long packRGBComponent(int value, final int index) {
        value = value % 256;
        if (value < 0) {
            value += 256;
        }
        return ImageBinaryCodec.pack(value, 8, index, INSTRUCTION_TYPE_BITS);
    }

    private static long pack(final int value, final int bits, final int index, final int offset) {
        NoHornyChecks.within(bits, 1, Integer.SIZE - 1, "bits");
        final var max = 1L << bits;
        NoHornyChecks.within(value, 0, (int) max - 1, "value");
        final var shift = (bits * index) + offset;
        NoHornyChecks.within(shift, 0, Long.SIZE - 1, "shift");
        return ((long) value) << shift;
    }

    public static VirtualBuilding.Group<? extends MindustryImage> decode(final InputStream input) throws IOException {
        final var in = new DataInputStream(input);

        final var elementCount = in.readInt();
        NoHornyChecks.within(elementCount, 1, MAXIMUM_GROUP_ELEMENT_COUNT, "element count");

        final var elements = new HashSet<VirtualBuilding<MindustryImage>>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int elementIndex = 0; elementIndex < elementCount; elementIndex++) {
            final var x = in.readInt();
            final var y = in.readInt();
            final var size = in.readInt();
            final var image = ImageBinaryCodec.decodeImage(in);
            final var element = new VirtualBuilding<>(x, y, size, image);

            elements.add(element);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + size);
            maxY = Math.max(maxY, y + size);
        }

        return new VirtualBuilding.Group<>(minX, minY, maxX - minX, maxY - minY, Collections.unmodifiableSet(elements));
    }

    private static MindustryImage decodeImage(final DataInputStream in) throws IOException {
        final var imageType = in.readByte();
        return switch (imageType) {
            case IMAGE_TYPE_CANVAS -> {
                final var resolution = in.readInt();
                NoHornyChecks.positive(resolution, "resolution");

                final var paletteLength = in.readInt();
                NoHornyChecks.positive(paletteLength, "palette length");
                final var palette = new int[paletteLength];
                for (int i = 0; i < palette.length; i++) {
                    palette[i] = in.readInt();
                }

                final var pixelsLength = in.readInt();
                NoHornyChecks.positive(pixelsLength, "pixels");
                final var pixels = new byte[pixelsLength];
                in.readFully(pixels);

                yield new MindustryCanvas(
                        resolution, ImmutableIntArray.wrap(palette), ImmutableByteArray.wrap(pixels), null);
            }
            case IMAGE_TYPE_DISPLAY -> {
                final var resolution = in.readInt();
                final var processors = new HashMap<ImmutablePoint2, MindustryDisplay.Processor>();

                final var processorCount = in.readInt();
                // TODO Raise min to 1 when implementing iterative grouping with filters
                NoHornyChecks.within(processorCount, 0, MAXIMUM_PROCESSOR_COUNT, "processor count");
                for (int i = 0; i < processorCount; i++) {
                    final var position = new ImmutablePoint2(in.readShort(), in.readShort());

                    final var instructionCount = in.readInt();
                    NoHornyChecks.within(instructionCount, 1, MAXIMUM_INSTRUCTION_COUNT, "instruction count");
                    final var instructions = new ArrayList<DrawInstruction>(instructionCount);
                    for (int j = 0; j < instructionCount; j++) {
                        instructions.add(ImageBinaryCodec.unpackInstruction(in.readLong()));
                    }

                    final var previous = processors.put(
                            position, new MindustryDisplay.Processor(Collections.unmodifiableList(instructions), null));
                    if (previous != null) {
                        throw new IOException("Duplicate display processor at " + position);
                    }
                }

                yield new MindustryDisplay(resolution, Collections.unmodifiableMap(processors));
            }
            default -> throw new IOException("Unknown image type: " + imageType);
        };
    }

    private static DrawInstruction unpackInstruction(final long packed) {
        return switch (ImageBinaryCodec.unpackInstructionType(packed)) {
            case INSTRUCTION_SET_COLOR ->
                new DrawInstruction.SetColor(
                        ImageBinaryCodec.unpackRGBComponent(packed, 0),
                        ImageBinaryCodec.unpackRGBComponent(packed, 1),
                        ImageBinaryCodec.unpackRGBComponent(packed, 2),
                        ImageBinaryCodec.unpackRGBComponent(packed, 3));
            case INSTRUCTION_DRAW_RECT ->
                new DrawInstruction.DrawRect(
                        ImageBinaryCodec.unpackCoordinate(packed, 0),
                        ImageBinaryCodec.unpackCoordinate(packed, 1),
                        ImageBinaryCodec.unpackCoordinate(packed, 2),
                        ImageBinaryCodec.unpackCoordinate(packed, 3));
            case INSTRUCTION_DRAW_TRIG ->
                new DrawInstruction.DrawTrig(
                        ImageBinaryCodec.unpackCoordinate(packed, 0),
                        ImageBinaryCodec.unpackCoordinate(packed, 1),
                        ImageBinaryCodec.unpackCoordinate(packed, 2),
                        ImageBinaryCodec.unpackCoordinate(packed, 3),
                        ImageBinaryCodec.unpackCoordinate(packed, 4),
                        ImageBinaryCodec.unpackCoordinate(packed, 5));
            default ->
                throw new IllegalArgumentException("Unknown packed instruction type: " + Long.toHexString(packed));
        };
    }

    private static int unpackInstructionType(final long packed) {
        return ImageBinaryCodec.unpack(packed, INSTRUCTION_TYPE_BITS, 0, 0);
    }

    private static int unpackCoordinate(final long packed, final int index) {
        return ImageBinaryCodec.unpack(packed, 9, index, INSTRUCTION_TYPE_BITS);
    }

    private static int unpackRGBComponent(final long packed, final int index) {
        return ImageBinaryCodec.unpack(packed, 8, index, INSTRUCTION_TYPE_BITS);
    }

    private static int unpack(final long packed, final int bits, final int index, final int offset) {
        NoHornyChecks.within(bits, 1, Integer.SIZE - 1, "bits");
        final var shift = (bits * index) + offset;
        NoHornyChecks.within(shift, 0, Long.SIZE - 1, "shift");
        final var mask = (1L << bits) - 1;
        return (int) ((packed >>> shift) & mask);
    }
}
