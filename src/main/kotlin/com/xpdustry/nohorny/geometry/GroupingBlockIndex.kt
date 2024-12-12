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
import arc.struct.IntSeq
import arc.struct.IntSet
import com.google.common.graph.ElementOrder
import com.google.common.graph.GraphBuilder
import kotlin.math.max
import kotlin.math.min

public interface GroupingBlockIndex<T : Any> {
    public fun select(
        x: Int,
        y: Int,
    ): IndexBlock<T>?

    public fun select(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ): Collection<IndexBlock<T>>

    public fun selectAll(): Collection<IndexBlock<T>>

    public fun insert(
        x: Int,
        y: Int,
        size: Int,
        data: T,
    ): Boolean

    public fun remove(
        x: Int,
        y: Int,
    ): IndexBlock<T>?

    public fun removeAll()

    public fun neighbors(
        x: Int,
        y: Int,
    ): Collection<IndexBlock<T>>

    public fun groups(): Collection<IndexGroup<T>>

    public companion object {
        @JvmStatic
        public fun <T : Any> create(group: GroupingFunction<T> = GroupingFunction.always()): GroupingBlockIndex<T> =
            GroupingBlockIndexImpl(group)
    }
}

@Suppress("UnstableApiUsage")
internal class GroupingBlockIndexImpl<T : Any>(private val group: GroupingFunction<T>) : GroupingBlockIndex<T> {
    private val index = IntMap<IndexBlock<T>>()
    internal val graph =
        GraphBuilder.undirected()
            .nodeOrder(ElementOrder.unordered<Int>())
            .build<Int>()

    override fun select(
        x: Int,
        y: Int,
    ): IndexBlock<T>? = index[Point2.pack(x, y)]

    override fun select(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ): Collection<IndexBlock<T>> =
        index.values()
            .filter { it.x in x until x + w && it.y in y until y + h }
            .fold(mutableSetOf()) { set, block -> set.apply { add(block) } }

    override fun selectAll() = index.values().toSet()

    override fun insert(
        x: Int,
        y: Int,
        size: Int,
        data: T,
    ): Boolean {
        require(size > 0) { "Size must be greater than 0" }

        val previous = select(x, y)
        if (previous != null) {
            return false
        }

        val block = IndexBlock(x, y, size, data)
        graph.addNode(Point2.pack(x, y))

        for (ix in x until x + size) {
            for (iy in y until y + size) {
                val packed = Point2.pack(ix, iy)
                index.put(packed, block)
            }
        }

        adjacent(x, y).forEach { other ->
            if (group.group(
                    IndexBlock.WithLinks(block, neighbors(block.x, block.y)),
                    IndexBlock.WithLinks(other, neighbors(other.x, other.y)),
                )
            ) {
                graph.putEdge(Point2.pack(block.x, block.y), Point2.pack(other.x, other.y))
            }
        }

        return true
    }

    override fun remove(
        x: Int,
        y: Int,
    ): IndexBlock<T>? {
        val block = select(x, y) ?: return null
        for (i in block.x until block.x + block.size) {
            for (j in block.y until block.y + block.size) {
                val packed = Point2.pack(i, j)
                index.remove(packed)
                graph.removeNode(packed)
            }
        }
        return block
    }

    override fun removeAll() {
        index.clear()
        graph.nodes().forEach { graph.removeNode(it) }
    }

    override fun neighbors(
        x: Int,
        y: Int,
    ): Collection<IndexBlock<T>> {
        val packed = Point2.pack(x, y)
        return if (graph.nodes().contains(packed)) graph.adjacentNodes(packed).map { index[it]!! } else emptyList()
    }

    override fun groups(): List<IndexGroup<T>> {
        val removing = IntSeq()
        val groups = mutableListOf<IndexGroup<T>>()
        val visited = IntSet()
        for (node in graph.nodes()) {
            if (node in visited) continue
            var x = Point2.x(node).toInt()
            var y = Point2.y(node).toInt()
            val size = index[node]?.size
            if (size == null) {
                // Very strange this even happens
                removing.add(node)
                continue
            }
            var w: Int = size
            var h: Int = size
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
                for (neighbor in graph.adjacentNodes(current)) {
                    queue.addLast(neighbor)
                }
            }
            groups += IndexGroup(x, y, w, h, blocks)
        }
        repeat(removing.size) {
            graph.removeNode(removing.get(it))
        }
        return groups
    }

    private fun adjacent(
        x: Int,
        y: Int,
    ): Collection<IndexBlock<T>> {
        val block = select(x, y) ?: return emptySet()
        val result = IntMap<IndexBlock<T>>()
        repeat(block.size) { i ->
            val neighbor = select(x - 1, y + i) ?: return@repeat
            result.put(Point2.pack(neighbor.x, neighbor.y), neighbor)
        }
        repeat(block.size) { i ->
            val neighbor = select(x + block.size, y + i) ?: return@repeat
            result.put(Point2.pack(neighbor.x, neighbor.y), neighbor)
        }
        repeat(block.size) { i ->
            val neighbor = select(x + i, y - 1) ?: return@repeat
            result.put(Point2.pack(neighbor.x, neighbor.y), neighbor)
        }
        repeat(block.size) { i ->
            val neighbor = select(x + i, y + block.size) ?: return@repeat
            result.put(Point2.pack(neighbor.x, neighbor.y), neighbor)
        }
        return result.values().toSet()
    }
}
