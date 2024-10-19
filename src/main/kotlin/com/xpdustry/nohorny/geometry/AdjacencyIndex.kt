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
package com.xpdustry.nohorny.geometry

import arc.math.geom.Point2
import arc.struct.IntMap
import arc.struct.IntQueue
import arc.struct.IntSet
import com.google.common.graph.ElementOrder
import com.google.common.graph.GraphBuilder
import kotlin.math.max
import kotlin.math.min

internal data class IndexBlock<T : Any>(
    override val x: Int,
    override val y: Int,
    val size: Int,
    val data: T,
) : Rectangle {
    override val w: Int get() = size
    override val h: Int get() = size
}

internal data class IndexCluster<T : Any>(
    override val x: Int,
    override val y: Int,
    override val w: Int,
    override val h: Int,
    val blocks: List<IndexBlock<T>>,
) : Rectangle

@Suppress("UnstableApiUsage")
internal class AdjacencyIndex<T : Any> {
    private val index = IntMap<IndexBlock<T>>()
    private val links =
        GraphBuilder.undirected()
            .nodeOrder(ElementOrder.unordered<Int>())
            .build<Int>()

    fun upsert(block: IndexBlock<T>) {
        if (select(block.x, block.y) != null) {
            remove(block.x, block.y)
        }
        for (x in block.x until block.x + block.size) {
            for (y in block.y until block.y + block.size) {
                val packed = Point2.pack(x, y)
                index.put(packed, block)

                if (x == block.x && select(x - 1, y) != null) {
                    links.putEdge(packed, Point2.pack(x - 1, y))
                } else if (x == block.x + block.size - 1 && select(x + 1, y) != null) {
                    links.putEdge(packed, Point2.pack(x + 1, y))
                }

                if (y == block.y && select(x, y - 1) != null) {
                    links.putEdge(packed, Point2.pack(x, y - 1))
                } else if (y == block.y + block.size - 1 && select(x, y + 1) != null) {
                    links.putEdge(packed, Point2.pack(x, y + 1))
                }
            }
        }
    }

    fun select(
        x: Int,
        y: Int,
    ): IndexBlock<T>? = index[Point2.pack(x, y)]

    fun remove(
        x: Int,
        y: Int,
    ) {
        val packed = Point2.pack(x, y)
        val block = select(x, y) ?: return
        for (i in block.x until block.x + block.size) {
            for (j in block.y until block.y + block.size) {
                index.remove(Point2.pack(i, j))
            }
        }
        links.removeNode(packed)
    }

    fun adjacents(): List<IndexCluster<T>> {
        val clusters = mutableListOf<IndexCluster<T>>()
        val visited = IntSet()
        for (node in links.nodes()) {
            if (node in visited) continue
            var x = Point2.x(node).toInt()
            var y = Point2.y(node).toInt()
            var w = index[node]!!.size
            var h = w
            val blocks = mutableListOf<IndexBlock<T>>()
            val queue = IntQueue()
            queue.addLast(node)
            while (!queue.isEmpty) {
                val current = queue.removeFirst()
                if (!visited.add(current)) continue
                val data = index[current]!!
                blocks += data
                x = min(x, data.x)
                y = min(y, data.y)
                w = max(w, data.x + data.size - x)
                h = max(h, data.y + data.size - y)
                for (neighbor in links.adjacentNodes(current)) {
                    queue.addLast(neighbor)
                }
            }
            clusters += IndexCluster(x, y, w, h, blocks)
        }
        return clusters
    }
}
