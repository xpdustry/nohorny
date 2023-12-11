/*
 * This file is part of NoHorny. The plugin securing your server against nsfw.
 *
 * MIT License
 *
 * Copyright (c) 2023 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.nohorny.geometry

import kotlin.math.max
import kotlin.math.min

public interface Rectangle {
    public val x: Int
    public val y: Int
    public val w: Int
    public val h: Int

    public fun contains(x: Int, y: Int): Boolean =
        x in this.x until this.x + this.w && y in this.y until this.y + this.h

    public fun isAdjacentOrContains(other: Rectangle): Boolean {
        val r1 = x + w + 1
        val l1 = x - 1
        val b1 = y - 1
        val t1 = y + h + 1

        val r2 = other.x + other.w + 1
        val l2 = other.x - 1
        val b2 = other.y - 1
        val t2 = other.y + other.h + 1

        val x1 = max(l1, l2)
        val y1 = max(b1, b2)
        val x2 = min(r1, r2)
        val y2 = min(t1, t2)

        return max(0, x2 - x1) * max(0, y2 - y1) > 4
    }
}
