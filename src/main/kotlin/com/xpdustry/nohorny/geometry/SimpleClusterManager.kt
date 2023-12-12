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

import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger

internal class SimpleClusterManager<T : Any> : ClusterManager<T> {

    private val generator = AtomicInteger(0)
    private val _clusters = mutableListOf<Cluster<T>>()
    override val clusters: List<Cluster<T>>
        get() = _clusters

    override fun getClusterByIdentifier(identifier: Int): Cluster<T>? =
        _clusters.firstOrNull { it.identifier == identifier }

    override fun getCluster(x: Int, y: Int): Cluster<T>? =
        _clusters.firstOrNull { cluster ->
            cluster.contains(x, y) && cluster.blocks.any { block -> block.contains(x, y) }
        }

    override fun getBlock(x: Int, y: Int): Cluster.Block<T>? {
        for (cluster in _clusters) {
            if (cluster.contains(x, y)) {
                for (block in cluster.blocks) {
                    if (block.contains(x, y)) {
                        return block
                    }
                }
            }
        }
        return null
    }

    override fun addBlock(block: Cluster.Block<T>): List<Int> {
        val existing = getCluster(block.x, block.y)
        if (existing != null) {
            error(
                "The location is occupied by the cluster (x=${existing.x}, y=${existing.y}, w=${existing.w}, h=${existing.h})")
        }

        val candidates = mutableListOf<Int>()
        for (i in _clusters.indices) {
            if (_clusters[i].blocks.any { it.isAdjacentOrContains(block) }) {
                candidates += i
            }
        }

        if (candidates.isEmpty()) {
            val cluster = Cluster(nextIdentifier(), listOf(block))
            _clusters += cluster
            return listOf(cluster.identifier)
        } else if (candidates.size == 1) {
            val cluster = _clusters[candidates[0]]
            _clusters[candidates[0]] = cluster.copy(blocks = cluster.blocks + block)
            return listOf(cluster.identifier)
        } else {
            val blocks = mutableListOf(block)
            val modified = mutableListOf<Int>()
            for ((shift, i) in candidates.withIndex()) {
                val target = _clusters.removeAt(i - shift)
                blocks += target.blocks
                modified += target.identifier
            }
            val cluster = Cluster(nextIdentifier(), blocks)
            _clusters += cluster
            modified += cluster.identifier
            return modified
        }
    }

    override fun removeBlock(x: Int, y: Int): List<Int> {
        var cIndex = -1
        var bIndex = -1
        for (i in _clusters.indices) {
            bIndex = _clusters[i].blocks.indexOfFirst { it.x == x && it.y == y }
            if (bIndex != -1) {
                cIndex = i
                break
            }
        }

        if (cIndex == -1) {
            return emptyList()
        }

        val previous = _clusters[cIndex]
        val blocks = LinkedList(previous.blocks.toMutableList().apply { removeAt(bIndex) })
        if (blocks.isEmpty()) {
            _clusters.removeAt(cIndex)
            return listOf(previous.identifier)
        }

        if (blocks.isEmpty()) {
            return listOf(previous.identifier)
        }

        val result = mutableListOf<Cluster<T>>()
        while (blocks.isNotEmpty()) {
            var cluster = Cluster(0, listOf(blocks.pop()))
            var added: Boolean
            do {
                added = false
                for (i in blocks.indices) {
                    if (cluster.isAdjacentOrContains(blocks[i])) {
                        cluster = cluster.copy(blocks = cluster.blocks + blocks.removeAt(i))
                        added = true
                        break
                    }
                }
            } while (blocks.isNotEmpty() && added)
            result += cluster
        }

        if (result.size == 1) {
            _clusters[cIndex] = result[0].copy(identifier = previous.identifier)
            return listOf(previous.identifier)
        }

        val modified = mutableListOf(previous.identifier)
        _clusters.removeAt(cIndex)
        for (cluster in result) {
            val updated = cluster.copy(identifier = nextIdentifier())
            _clusters += updated
            modified += updated.identifier
        }

        return modified
    }

    override fun clear() {
        _clusters.clear()
    }

    private fun nextIdentifier() = generator.getAndUpdate { it.inc().coerceAtLeast(0) }
}
