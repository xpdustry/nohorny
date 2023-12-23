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
package com.xpdustry.nohorny

import arc.struct.IntIntMap
import com.xpdustry.nohorny.geometry.ImmutablePoint
import java.net.InetAddress

public sealed interface NoHornyImage {
    public val resolution: Int

    public fun copy(): NoHornyImage

    public data class Canvas(
        override val resolution: Int,
        public val pixels: IntIntMap,
        public val author: Author?
    ) : NoHornyImage {
        override fun copy(): Canvas = Canvas(resolution, IntIntMap(pixels), author)
    }

    public data class Display(
        override val resolution: Int,
        public val processors: MutableMap<ImmutablePoint, Processor>
    ) : NoHornyImage {
        override fun copy(): Display = Display(resolution, processors.toMutableMap())
    }

    public data class Processor(val instructions: List<Instruction>, val author: Author?)

    public sealed interface Instruction {
        public data class Color(val r: Int, val g: Int, val b: Int, val a: Int) : Instruction

        public data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) : Instruction

        public data class Triangle(
            val x1: Int,
            val y1: Int,
            val x2: Int,
            val y2: Int,
            val x3: Int,
            val y3: Int
        ) : Instruction
    }

    public data class Author(val uuid: String, val address: InetAddress)
}
