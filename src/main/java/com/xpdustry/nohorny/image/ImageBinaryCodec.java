// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.image;

import com.xpdustry.nohorny.geometry.ImmutablePoint2;
import com.xpdustry.nohorny.geometry.VirtualBuilding;
import com.xpdustry.nohorny.struct.ImmutableByteArray;
import com.xpdustry.nohorny.struct.ImmutableIntArray;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeMap;

// TODO Works but unreadable, we should wrap the output stream with a dedicated frameWriter instead
public final class ImageBinaryCodec {

    private static final int IMAGE_TYPE_CANVAS = 0;
    private static final int IMAGE_TYPE_DISPLAY = 1;

    private static final int INSTRUCTION_SET_COLOR = 0;
    private static final int INSTRUCTION_DRAW_RECT = 1;
    private static final int INSTRUCTION_DRAW_TRIG = 2;

    private static final int INSTRUCTION_TYPE_BITS = 2;
    private static final int ELEMENT_COORD_BITS = 16;

    public void encode(
            final OutputStream output, final List<? extends VirtualBuilding.Group<? extends MindustryImage>> groups)
            throws IOException {
        final var out = new DataOutputStream(output);
        writeFrame(out, groups.size());
        for (final var group : groups) {
            writeFrame(out, group.elements().size());
            for (final var element : group.elements()) {
                writeFrame(out, packElement(element.x(), element.y(), element.size()));
                writeImage(out, element.data());
            }
        }
        out.flush();
    }

    private static void writeImage(final DataOutputStream out, final MindustryImage image) throws IOException {
        switch (image) {
            case MindustryCanvas canvas -> {
                writeFrame(out, IMAGE_TYPE_CANVAS);
                writeFrame(out, canvas.resolution());
                writeFrame(out, canvas.palette().length());
                for (int i = 0; i < canvas.palette().length(); i++) {
                    writeFrame(out, Integer.toUnsignedLong(canvas.palette().get(i)));
                }
                writeFrame(out, canvas.pixels().length());
                for (int i = 0; i < canvas.pixels().length(); i++) {
                    writeFrame(out, Byte.toUnsignedLong(canvas.pixels().get(i)));
                }
            }
            case MindustryDisplay display -> {
                writeFrame(out, IMAGE_TYPE_DISPLAY);
                writeFrame(out, display.resolution());
                final var processors = display.processors().entrySet();
                writeFrame(out, processors.size());
                for (final var processor : processors) {
                    final var data = processor.getValue();
                    writeFrame(out, processor.getKey().x());
                    writeFrame(out, processor.getKey().y());
                    writeFrame(out, data.links().size());
                    for (final var link : data.links()) {
                        writeFrame(out, link.x());
                        writeFrame(out, link.y());
                    }
                    writeFrame(out, data.instructions().size());
                    for (final var instruction : data.instructions()) {
                        writeFrame(out, packInstruction(instruction));
                    }
                }
            }
        }
    }

    private static long packInstruction(final DrawInstruction instruction) throws IOException {
        return switch (instruction) {
            case DrawInstruction.SetColor(int r, int g, int b, int a) ->
                INSTRUCTION_SET_COLOR | pack8(r, 0) | pack8(g, 1) | pack8(b, 2) | pack8(a, 3);
            case DrawInstruction.DrawRect(int x, int y, int w, int h) ->
                INSTRUCTION_DRAW_RECT | pack9(x, 0) | pack9(y, 1) | pack9(w, 2) | pack9(h, 3);
            case DrawInstruction.DrawTrig(int x1, int y1, int x2, int y2, int x3, int y3) ->
                INSTRUCTION_DRAW_TRIG
                        | pack9(x1, 0)
                        | pack9(y1, 1)
                        | pack9(x2, 2)
                        | pack9(y2, 3)
                        | pack9(x3, 4)
                        | pack9(y3, 5);
        };
    }

    private static long pack8(final int value, final int index) throws IOException {
        if (value < 0 || value > 0xFF) {
            throw new IOException("8-bit instruction value out of range: " + value);
        }
        return (long) value << (INSTRUCTION_TYPE_BITS + (index * 8));
    }

    private static long pack9(final int value, final int index) throws IOException {
        if (value < 0 || value > 0x1FF) {
            throw new IOException("9-bit instruction value out of range: " + value);
        }
        return (long) value << (INSTRUCTION_TYPE_BITS + (index * 9));
    }

    private static void writeFrame(final DataOutputStream out, final int value) throws IOException {
        out.writeLong(value);
    }

    private static void writeFrame(final DataOutputStream out, final long value) throws IOException {
        out.writeLong(value);
    }

    public List<VirtualBuilding.Group<MindustryImage>> decode(final InputStream input) throws IOException {
        final var in = new DataInputStream(input);
        final int groupCount = readFrameAsInt(in);
        final var groups = new ArrayList<VirtualBuilding.Group<MindustryImage>>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            final int elementCount = readFrameAsInt(in);
            if (elementCount < 1) {
                throw new IOException("group must contain at least one element");
            }

            final var elements = new LinkedHashSet<VirtualBuilding<MindustryImage>>(elementCount);
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int j = 0; j < elementCount; j++) {
                final long frame = readFrame(in);
                final int x = unpackSigned16(frame, 0);
                final int y = unpackSigned16(frame, 1);
                final int size = unpackUnsigned16(frame, 2);
                final var image = readImage(in);
                elements.add(new VirtualBuilding<>(x, y, size, image));
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + size);
                maxY = Math.max(maxY, y + size);
            }
            groups.add(new VirtualBuilding.Group<>(minX, minY, maxX - minX, maxY - minY, elements));
        }
        return List.copyOf(groups);
    }

    private static MindustryImage readImage(final DataInputStream in) throws IOException {
        final int type = readFrameAsInt(in);
        return switch (type) {
            case IMAGE_TYPE_CANVAS -> readCanvas(in);
            case IMAGE_TYPE_DISPLAY -> readDisplay(in);
            default -> throw new IOException("unknown image type: " + type);
        };
    }

    private static MindustryCanvas readCanvas(final DataInputStream in) throws IOException {
        final int resolution = readFrameAsInt(in);
        final int paletteLength = readFrameAsInt(in);
        final var palette = new int[paletteLength];
        for (int i = 0; i < paletteLength; i++) {
            palette[i] = readFrameAsInt(in);
        }
        final int pixelsLength = readFrameAsInt(in);
        final var pixels = new byte[pixelsLength];
        for (int i = 0; i < pixelsLength; i++) {
            pixels[i] = (byte) readFrameAsInt(in);
        }
        return new MindustryCanvas(resolution, ImmutableIntArray.wrap(palette), ImmutableByteArray.wrap(pixels), null);
    }

    private static MindustryDisplay readDisplay(final DataInputStream in) throws IOException {
        final int resolution = readFrameAsInt(in);
        final int processorCount = readFrameAsInt(in);
        final var processors = new TreeMap<ImmutablePoint2, MindustryProcessor>();
        for (int i = 0; i < processorCount; i++) {
            final var point = new ImmutablePoint2(readFrameAsInt(in), readFrameAsInt(in));
            final int linkCount = readFrameAsInt(in);
            final var links = new ArrayList<ImmutablePoint2>(linkCount);
            for (int j = 0; j < linkCount; j++) {
                links.add(new ImmutablePoint2(readFrameAsInt(in), readFrameAsInt(in)));
            }
            final int instructionCount = readFrameAsInt(in);
            final var instructions = new ArrayList<DrawInstruction>(instructionCount);
            for (int j = 0; j < instructionCount; j++) {
                instructions.add(unpackInstruction(readFrame(in)));
            }
            processors.put(point, new MindustryProcessor(List.copyOf(instructions), List.copyOf(links), null));
        }
        return new MindustryDisplay(resolution, Collections.unmodifiableSortedMap(processors));
    }

    private static DrawInstruction unpackInstruction(final long frame) throws IOException {
        return switch ((int) (frame & 0b11L)) {
            case INSTRUCTION_SET_COLOR ->
                new DrawInstruction.SetColor(
                        unpack8(frame, 0), unpack8(frame, 1), unpack8(frame, 2), unpack8(frame, 3));
            case INSTRUCTION_DRAW_RECT ->
                new DrawInstruction.DrawRect(
                        unpack9(frame, 0), unpack9(frame, 1), unpack9(frame, 2), unpack9(frame, 3));
            case INSTRUCTION_DRAW_TRIG ->
                new DrawInstruction.DrawTrig(
                        unpack9(frame, 0),
                        unpack9(frame, 1),
                        unpack9(frame, 2),
                        unpack9(frame, 3),
                        unpack9(frame, 4),
                        unpack9(frame, 5));
            default -> throw new IOException("unknown instruction type");
        };
    }

    private static int unpack8(final long frame, final int index) {
        return (int) ((frame >>> (INSTRUCTION_TYPE_BITS + (index * 8))) & 0x0FF);
    }

    private static int unpack9(final long frame, final int index) {
        return (int) ((frame >>> (INSTRUCTION_TYPE_BITS + (index * 9))) & 0x1FF);
    }

    private static long packElement(final int x, final int y, final int size) throws IOException {
        return packSigned16(x, 0) | packSigned16(y, 1) | packUnsigned16(size, 2);
    }

    private static long packSigned16(final int value, final int index) throws IOException {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IOException("16-bit signed frame value out of range: " + value);
        }
        return (Integer.toUnsignedLong(value & 0xFFFF)) << (index * ELEMENT_COORD_BITS);
    }

    private static long packUnsigned16(final int value, final int index) throws IOException {
        if (value < 0 || value > 0xFFFF) {
            throw new IOException("16-bit unsigned frame value out of range: " + value);
        }
        return (long) value << (index * ELEMENT_COORD_BITS);
    }

    private static int unpackSigned16(final long frame, final int index) {
        return (short) ((frame >>> (index * ELEMENT_COORD_BITS)) & 0xFFFF);
    }

    private static int unpackUnsigned16(final long frame, final int index) {
        return (int) ((frame >>> (index * ELEMENT_COORD_BITS)) & 0xFFFF);
    }

    private static long readFrame(final DataInputStream in) throws IOException {
        return in.readLong();
    }

    private static int readFrameAsInt(final DataInputStream in) throws IOException {
        final long value = readFrame(in);
        if (value < Integer.MIN_VALUE || value > 0xFFFF_FFFFL) {
            throw new IOException("frame value out of int range: " + value);
        }
        return (int) value;
    }
}
