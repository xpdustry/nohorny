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
package com.xpdustry.nohorny.tracker

import arc.graphics.Color
import arc.math.geom.Point2
import arc.struct.IntIntMap
import arc.struct.IntMap
import com.xpdustry.nohorny.NoHornyConfig
import com.xpdustry.nohorny.NoHornyListener
import com.xpdustry.nohorny.extension.asAuthor
import com.xpdustry.nohorny.extension.onBuildingLifecycleEvent
import com.xpdustry.nohorny.extension.onEvent
import com.xpdustry.nohorny.extension.rx
import com.xpdustry.nohorny.extension.ry
import com.xpdustry.nohorny.geometry.GroupingBlockIndex
import com.xpdustry.nohorny.geometry.IndexGroup
import com.xpdustry.nohorny.image.ImageProcessor
import com.xpdustry.nohorny.image.NoHornyImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mindustry.game.EventType
import mindustry.world.blocks.logic.CanvasBlock
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

internal data class CanvasesConfig(
    val minimumGroupSize: Int = 9,
) {
    init {
        require(minimumGroupSize >= 1) { "Minimum group size must be at least 1" }
    }
}

internal class CanvasesTracker(
    private val config: () -> NoHornyConfig,
    private val processor: ImageProcessor,
) : NoHornyListener("Canvases Tracker", Dispatchers.Default.limitedParallelism(1)) {
    private val canvases = GroupingBlockIndex.create<NoHornyImage.Canvas>()
    private val marked = IntMap<Long>()
    private val groups: AtomicReference<List<IndexGroup<NoHornyImage.Canvas>>> = AtomicReference(emptyList())

    override fun onInit() {
        onBuildingLifecycleEvent<CanvasBlock.CanvasBuild>(
            insert = { canvas, player, new ->
                val resolution = (canvas.block as CanvasBlock).canvasSize
                val x = canvas.rx
                val y = canvas.ry
                val size = canvas.block.size
                val pixels = readCanvas(canvas)
                scope.launch {
                    canvases.insert(
                        x,
                        y,
                        size,
                        NoHornyImage.Canvas(resolution, pixels, player?.asAuthor()),
                    )
                    if (new) {
                        marked.put(Point2.pack(x, y), System.currentTimeMillis())
                    }
                }
            },
            remove = { x, y ->
                scope.launch {
                    canvases.remove(x, y)
                    marked.remove(Point2.pack(x, y))
                }
            },
        )

        onEvent<EventType.ResetEvent> {
            scope.launch {
                canvases.removeAll()
                marked.clear()
            }
        }

        scope.launch {
            withContext(Dispatchers.Default) {
                while (isActive) {
                    delay(config().processingDelay)
                    scope.launch { update() }
                }
            }
        }
    }

    private fun update() {
        val config = config()
        groups.set(canvases.groups().toList())
        for (group in groups.get()) {
            val now = System.currentTimeMillis()
            val lastMod =
                group.blocks.asSequence()
                    .mapNotNull { marked.get(Point2.pack(it.x, it.y)) }
                    .maxOrNull()
                    ?: now
            val elapsed = (now - lastMod).milliseconds
            if (
                elapsed > config.processingDelay / 2 &&
                group.blocks.size >= config.canvases.minimumGroupSize
            ) {
                processor.process(group)
                for (block in group.blocks) {
                    marked.remove(Point2.pack(block.x, block.y))
                }
            }
        }
    }

    private fun readCanvas(canvas: CanvasBlock.CanvasBuild): IntIntMap {
        val block = canvas.block as CanvasBlock
        val pixels = IntIntMap()
        val temp = Color()

        for (i in 0 until block.canvasSize * block.canvasSize) {
            val bitOffset = i * block.bitsPerPixel
            val pal = getByte(block, canvas.data, bitOffset)
            temp.set(block.palette[pal])
            pixels.put(i, temp.rgb888())
        }

        return pixels
    }

    // Anuke black magic
    private fun getByte(
        block: CanvasBlock,
        data: ByteArray,
        bitOffset: Int,
    ): Int {
        var result = 0
        for (i in 0 until block.bitsPerPixel) {
            val word = i + bitOffset ushr 3
            result = result or ((if (data[word].toInt() and (1 shl (i + bitOffset and 7)) == 0) 0 else 1) shl i)
        }
        return result
    }
}
