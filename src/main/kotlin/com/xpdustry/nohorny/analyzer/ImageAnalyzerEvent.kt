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
package com.xpdustry.nohorny.analyzer

import com.xpdustry.nohorny.NoHornyImage
import com.xpdustry.nohorny.geometry.Cluster

public data class ImageAnalyzerEvent(
    val result: ImageAnalyzer.Result,
    val cluster: Cluster<out NoHornyImage>
) {
    public operator fun component3(): NoHornyImage.Author? = author

    val author: NoHornyImage.Author?
        get() =
            cluster.blocks
                .flatMap { block ->
                    when (block.payload) {
                        is NoHornyImage.Canvas -> listOf(block.payload.author)
                        is NoHornyImage.Display -> block.payload.processors.values.map { it.author }
                    }
                }
                .let { authors ->
                    val max =
                        authors
                            .groupingBy(NoHornyImage.Author::address)
                            .eachCount()
                            .maxByOrNull { it.value }
                            ?.key ?: return@let null
                    return authors.firstOrNull { it.address == max }
                }
}
