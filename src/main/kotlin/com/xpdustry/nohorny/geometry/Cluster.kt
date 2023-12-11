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

public data class Cluster<T : Any>(val identifier: Int, val blocks: List<Block<T>>) : Rectangle {

    override val x: Int = (blocks.minOfOrNull { it.x } ?: 0)
    override val y: Int = (blocks.minOfOrNull { it.y } ?: 0)
    override val w: Int = (blocks.maxOfOrNull { it.x + it.size } ?: 0) - x
    override val h: Int = (blocks.maxOfOrNull { it.y + it.size } ?: 0) - y

    public data class Block<T : Any>(
        override var x: Int,
        override var y: Int,
        val size: Int,
        val payload: T
    ) : Rectangle {
        override val w: Int
            get() = size

        override val h: Int
            get() = size
    }
}
