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

    public fun upsert(
        x: Int,
        y: Int,
        size: Int,
        data: T,
    ): IndexBlock<T>?

    public fun remove(
        x: Int,
        y: Int,
    ): IndexBlock<T>?

    public fun removeAll()

    public fun neighbors(
        x: Int,
        y: Int,
    ): Collection<IndexBlock<T>>

    public fun groups(): Collection<BlockGroup<T>>

    public companion object {
        @JvmStatic
        public fun <T : Any> create(group: GroupingFunction<T> = GroupingFunction.always()): GroupingBlockIndex<T> =
            GroupingBlockIndexImpl(group)
    }
}

public data class IndexBlock<T : Any>(
    val x: Int,
    val y: Int,
    val size: Int,
    val data: T,
) {
    public data class WithLinks<T : Any>(
        val block: IndexBlock<T>,
        val links: Collection<IndexBlock<T>>,
    )
}

public data class BlockGroup<T : Any>(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val blocks: List<IndexBlock<T>>,
)

public fun interface GroupingFunction<T : Any> {
    public fun group(
        a: IndexBlock.WithLinks<T>,
        b: IndexBlock.WithLinks<T>,
    ): Boolean

    public companion object {
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        public fun <T : Any> always(): GroupingFunction<T> = Always as GroupingFunction<T>

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        public fun <T : Any> single(): GroupingFunction<T> = Single as GroupingFunction<T>
    }

    private object Always : GroupingFunction<Any> {
        override fun group(
            a: IndexBlock.WithLinks<Any>,
            b: IndexBlock.WithLinks<Any>,
        ): Boolean = true
    }

    private object Single : GroupingFunction<Any> {
        override fun group(
            a: IndexBlock.WithLinks<Any>,
            b: IndexBlock.WithLinks<Any>,
        ): Boolean = false
    }
}

@Suppress("UnstableApiUsage")
internal class GroupingBlockIndexImpl<T : Any>(private val group: GroupingFunction<T>) : GroupingBlockIndex<T> {
    private val index = IntMap<IndexBlock<T>>()
    private val graph =
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

    override fun upsert(
        x: Int,
        y: Int,
        size: Int,
        data: T,
    ): IndexBlock<T>? {
        require(size > 0) { "Size must be greater than 0" }

        val previous = select(x, y)
        if (previous != null) {
            remove(x, y)
        }

        val block = IndexBlock(x, y, size, data)

        graph.addNode(Point2.pack(x, y))
        for (ix in x until x + size) {
            for (iy in y until y + size) {
                val packed = Point2.pack(ix, iy)
                index.put(packed, block)

                if (ix == x && canBeGroupedWith(block, ix - 1, iy)) {
                    val other = select(ix - 1, iy)!!
                    graph.putEdge(packed, Point2.pack(other.x, other.y))
                }
                if (ix == x + size - 1 && canBeGroupedWith(block, ix + 1, iy)) {
                    val other = select(ix + 1, iy)!!
                    graph.putEdge(packed, Point2.pack(other.x, other.y))
                }
                if (iy == y && canBeGroupedWith(block, ix, iy - 1)) {
                    val other = select(ix, iy - 1)!!
                    graph.putEdge(packed, Point2.pack(other.x, other.y))
                }
                if (iy == y + size - 1 && canBeGroupedWith(block, ix, iy + 1)) {
                    val other = select(ix, iy + 1)!!
                    graph.putEdge(packed, Point2.pack(other.x, other.y))
                }
            }
        }

        return previous
    }

    override fun remove(
        x: Int,
        y: Int,
    ): IndexBlock<T>? {
        val packed = Point2.pack(x, y)
        val block = select(x, y) ?: return null
        for (i in block.x until block.x + block.size) {
            for (j in block.y until block.y + block.size) {
                index.remove(Point2.pack(i, j))
            }
        }
        graph.removeNode(packed)
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

    override fun groups(): List<BlockGroup<T>> {
        val clusters = mutableListOf<BlockGroup<T>>()
        val visited = IntSet()
        for (node in graph.nodes()) {
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
                for (neighbor in graph.adjacentNodes(current)) {
                    queue.addLast(neighbor)
                }
            }
            clusters += BlockGroup(x, y, w, h, blocks)
        }
        return clusters
    }

    private fun canBeGroupedWith(
        block: IndexBlock<T>,
        x2: Int,
        y2: Int,
    ): Boolean {
        val other = select(x2, y2) ?: return false
        return group.group(
            IndexBlock.WithLinks(block, neighbors(block.x, block.y)),
            IndexBlock.WithLinks(other, neighbors(x2, y2)),
        )
    }
}
