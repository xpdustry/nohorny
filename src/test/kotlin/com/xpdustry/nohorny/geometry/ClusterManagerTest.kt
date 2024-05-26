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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClusterManagerTest {

    @Test
    fun `test blocks that share a side`() {
        val manager = createManager()
        manager.addBlock(createBlock(0, 0, 1))
        manager.addBlock(createBlock(1, 0, 1))
        Assertions.assertEquals(1, manager.clusters.size)

        val cluster = manager.clusters[0]
        Assertions.assertEquals(2, cluster.blocks.size)
        Assertions.assertEquals(0, cluster.x)
        Assertions.assertEquals(0, cluster.y)
        Assertions.assertEquals(2, cluster.w)
        Assertions.assertEquals(1, cluster.h)
    }

    @Test
    fun `test blocks that do not share a side`() {
        val manager = createManager()
        manager.addBlock(createBlock(2, 2, 2))
        manager.addBlock(createBlock(-2, 0, 1))
        manager.addBlock(createBlock(10, 10, 10))
        Assertions.assertEquals(3, manager.clusters.size)
    }

    @Test
    fun `test blocks that partially share a side`() {
        val manager = createManager()
        manager.addBlock(createBlock(1, 1, 2))
        manager.addBlock(createBlock(3, 2, 2))
        Assertions.assertEquals(1, manager.clusters.size)
    }

    @Test
    fun `test blocks that only share a corner`() {
        val manager = createManager()
        manager.addBlock(createBlock(0, 0, 1))
        manager.addBlock(createBlock(1, 1, 1))
        Assertions.assertEquals(2, manager.clusters.size)
    }

    @Test
    fun `test block remove`() {
        val manager = createManager()
        for (x in 0..2) {
            for (y in 0..5) {
                manager.addBlock(createBlock(x, y, 1))
            }
        }

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(18, manager.clusters[0].blocks.size)

        manager.removeBlock(0, 1)
        manager.removeBlock(1, 1)

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(16, manager.clusters[0].blocks.size)
    }

    @Test
    fun `test block remove from within`() {
        val manager = createManager()
        for (x in 0..4) {
            for (y in 0..4) {
                manager.addBlock(createBlock(x, y, 1))
            }
        }

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(25, manager.clusters[0].blocks.size)

        // Removes a U shape inside the 5 by 5 square
        for (x in 1..3) {
            for (y in 1..3) {
                if (x == 1 && (y == 1 || y == 2)) continue
                manager.removeBlock(x, y)
            }
        }

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(18, manager.clusters[0].blocks.size)
    }

    @Test
    fun `test cluster split`() {
        val manager = createManager()
        for (x in 0..2) {
            manager.addBlock(createBlock(x, 0, 1))
        }
        manager.addBlock(createBlock(1, 1, 1))
        Assertions.assertEquals(1, manager.clusters.size)
        manager.removeBlock(1, 0)
        Assertions.assertEquals(3, manager.clusters.size)
    }

    @Test
    fun `test cluster merge`() {
        val manager = createManager()
        for (y in 0..2) {
            for (x in 0..2) {
                manager.addBlock(createBlock(x, y * 2, 1))
            }
        }
        Assertions.assertEquals(3, manager.clusters.size)
        manager.addBlock(createBlock(1, 1, 1))
        Assertions.assertEquals(2, manager.clusters.size)
        manager.addBlock(createBlock(1, 3, 1))
        Assertions.assertEquals(1, manager.clusters.size)
    }

    @Test
    fun `test error on add to occupied`() {
        val manager = createManager()
        manager.addBlock(createBlock(0, 0, 1))
        assertThrows<IllegalStateException> { manager.addBlock(createBlock(0, 0, 1)) }
    }

    @Test
    fun `test cluster on same axis spaced by 1`() {
        val manager = createManager()
        manager.addBlock(createBlock(0, 0, 6))
        manager.addBlock(createBlock(7, 0, 6))
        manager.addBlock(createBlock(0, 7, 6))
        manager.addBlock(createBlock(7, 7, 6))
        Assertions.assertEquals(4, manager.clusters.size)
    }

    private fun createManager() = ClusterManager.create<Unit>()

    private fun createBlock(x: Int, y: Int, size: Int) = Cluster.Block(x, y, size, Unit)
}
