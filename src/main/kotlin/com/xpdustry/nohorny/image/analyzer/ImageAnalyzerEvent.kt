/*
 * This file is part of NoHorny. The plugin securing your server against nsfw builds.
 *
 * MIT License
 *
 * Copyright (c) 2024 Xpdustry
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
package com.xpdustry.nohorny.image.analyzer

import com.xpdustry.nohorny.geometry.IndexGroup
import com.xpdustry.nohorny.image.NoHornyImage
import com.xpdustry.nohorny.image.NoHornyResult
import java.awt.image.BufferedImage
import java.net.InetAddress

public data class ImageAnalyzerEvent(
    val result: NoHornyResult,
    val group: IndexGroup<out NoHornyImage>,
    val image: BufferedImage,
    val author: NoHornyImage.Author?,
) {
    public constructor(
        result: NoHornyResult,
        group: IndexGroup<out NoHornyImage>,
        image: BufferedImage,
    ) : this(result, group, image, computeAuthor(group))
}

private fun computeAuthor(group: IndexGroup<out NoHornyImage>): NoHornyImage.Author? {
    val authors =
        group.blocks.flatMap { block ->
            when (block.data) {
                is NoHornyImage.Canvas -> listOf(block.data.author)
                is NoHornyImage.Display -> block.data.processors.values.map { it.author }
            }
        }
    val safe = authors.filterNotNull()
    if (safe.isNotEmpty() && (safe.size / authors.size) < 0.4) {
        return null
    }
    val counts = mutableMapOf<InetAddress, Int>()
    var author: NoHornyImage.Author? = null
    for (entry in safe) {
        counts.compute(entry.address) { _, v -> (v ?: 0) + 1 }
        if (author == null || counts[entry.address]!! > counts[author.address]!!) {
            author = entry
        }
    }
    return author
}
