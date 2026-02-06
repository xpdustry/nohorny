// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.image;

public sealed interface DrawInstruction {

    record SetColor(int r, int g, int b, int a) implements DrawInstruction {}

    record DrawRect(int x, int y, int w, int h) implements DrawInstruction {}

    record DrawTrig(int x1, int y1, int x2, int y2, int x3, int y3) implements DrawInstruction {}
}
