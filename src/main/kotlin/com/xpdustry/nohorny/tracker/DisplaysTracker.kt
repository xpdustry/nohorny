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

import arc.math.geom.Point2
import arc.struct.IntMap
import com.google.common.collect.ImmutableMap
import com.xpdustry.nohorny.NoHornyConfig
import com.xpdustry.nohorny.NoHornyListener
import com.xpdustry.nohorny.extension.asAuthor
import com.xpdustry.nohorny.extension.onBuildingLifecycleEvent
import com.xpdustry.nohorny.extension.onEvent
import com.xpdustry.nohorny.extension.rx
import com.xpdustry.nohorny.extension.ry
import com.xpdustry.nohorny.geometry.GroupingBlockIndex
import com.xpdustry.nohorny.geometry.GroupingFunction
import com.xpdustry.nohorny.geometry.ImmutablePoint
import com.xpdustry.nohorny.geometry.IndexGroup
import com.xpdustry.nohorny.image.ImageProcessor
import com.xpdustry.nohorny.image.NoHornyImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mindustry.Vars
import mindustry.game.EventType
import mindustry.logic.LExecutor
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.LogicDisplay
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

internal data class DisplaysConfig(
    val processorSearchRadius: Int = 10,
    val minimumInstructionCount: Int = 100,
    val minimumProcessorCount: Int = 5,
) {
    init {
        require(processorSearchRadius >= 1) { "processorSearchRadius cannot be lower than 1" }
        require(minimumInstructionCount >= 1) { "minimumInstructionCount cannot be lower than 1" }
        require(minimumProcessorCount >= 1) { "minimumProcessorCount cannot be lower than 1" }
    }
}

internal class DisplaysTracker(
    private val config: () -> NoHornyConfig,
    private val processor: ImageProcessor,
) : NoHornyListener("Display Tracker", Dispatchers.Default.limitedParallelism(1)) {
    private val processors = GroupingBlockIndex.create<NoHornyImage.Processor>(GroupingFunction.single())
    private val displays = GroupingBlockIndex.create<NoHornyImage.Display>()
    private val marked = IntMap<Long>()
    private val groups: AtomicReference<List<IndexGroup<NoHornyImage.Display>>> = AtomicReference(emptyList())

    override fun onInit() {
        onBuildingLifecycleEvent<LogicDisplay.LogicDisplayBuild>(
            insert = { display, _, _ ->
                val resolution = (display.block as LogicDisplay).displaySize
                val map = HashMap<ImmutablePoint, NoHornyImage.Processor>()

                val config = config().displays
                for ((x, y, _, data) in processors.select(
                    display.tileX() - (config.processorSearchRadius / 2),
                    display.tileY() - (config.processorSearchRadius / 2),
                    config.processorSearchRadius,
                    config.processorSearchRadius,
                )) {
                    if (display.within(
                            x.toFloat() * Vars.tilesize,
                            y.toFloat() * Vars.tilesize,
                            config.processorSearchRadius.toFloat() * Vars.tilesize,
                        )
                    ) {
                        val point = ImmutablePoint(x, y)
                        for (link in data.links) {
                            if (link.x in display.rx until display.rx + display.block.size &&
                                link.y in display.ry until display.ry + display.block.size
                            ) {
                                if (map.containsKey(point)) {
                                    logger.debug(
                                        "Processor at {} is already linked to the display at ({}, {})",
                                        point,
                                        display.rx,
                                        display.ry,
                                    )
                                }
                                map[point] = data
                            }
                        }
                    }
                }

                val x = display.rx
                val y = display.ry
                val size = display.block.size

                scope.launch {
                    displays.insert(x, y, size, NoHornyImage.Display(resolution, Collections.unmodifiableMap(map)))
                    marked.put(Point2.pack(display.rx, display.ry), System.currentTimeMillis())
                }
            },
            remove = { x, y ->
                scope.launch {
                    displays.remove(x, y)
                    marked.remove(Point2.pack(x, y))
                }
            },
        )

        onBuildingLifecycleEvent<LogicBlock.LogicBuild>(
            insert = { processor, player, _ ->
                val instructions = readInstructions(processor.executor)
                val links = processor.links.select { it.active }.map { ImmutablePoint(it.x, it.y) }

                if (instructions.size < config().displays.minimumInstructionCount || links.isEmpty) {
                    return@onBuildingLifecycleEvent
                }

                val data = NoHornyImage.Processor(instructions, player?.asAuthor(), links.list())
                val point = ImmutablePoint(processor.tileX(), processor.tileY())
                val x = processor.rx
                val y = processor.ry
                val size = processor.block.size

                scope.launch {
                    processors.insert(x, y, size, data)
                    for (link in links) {
                        val element = displays.select(link.x, link.y) ?: continue
                        displays.insert(
                            element.x,
                            element.y,
                            element.size,
                            element.data.copy(processors = Collections.unmodifiableMap(element.data.processors + (point to data))),
                        )
                        marked.put(Point2.pack(element.x, element.y), System.currentTimeMillis())
                    }
                }
            },
            remove = { x, y ->
                scope.launch {
                    val point = ImmutablePoint(x, y)
                    val block = processors.remove(x, y) ?: return@launch
                    for (link in block.data.links) {
                        val element = displays.select(link.x, link.y) ?: continue
                        displays.insert(
                            element.x,
                            element.y,
                            element.size,
                            element.data.copy(
                                processors = ImmutableMap.copyOf(element.data.processors - point),
                            ),
                        )
                        marked.put(Point2.pack(element.x, element.y), System.currentTimeMillis())
                    }
                }
            },
        )

        onEvent<EventType.ResetEvent> {
            scope.launch {
                processors.removeAll()
                displays.removeAll()
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
        groups.set(displays.groups().toList())
        for (group in groups.get()) {
            val now = System.currentTimeMillis()
            val lastMod =
                group.blocks.asSequence().mapNotNull { marked.get(Point2.pack(it.x, it.y)) }.maxOrNull()
                    ?: now
            val elapsed = (now - lastMod).milliseconds
            if (elapsed > (config.processingDelay / 2) &&
                group.blocks.sumOf { it.data.processors.size } >= config.displays.minimumProcessorCount
            ) {
                processor.process(group)
                for (block in group.blocks) {
                    marked.remove(Point2.pack(block.x, block.y))
                }
            }
        }
    }

    private fun readInstructions(executor: LExecutor): List<NoHornyImage.Instruction> {
        val instructions = mutableListOf<NoHornyImage.Instruction>()
        for (instruction in executor.instructions) {
            if (instruction !is LExecutor.DrawI) {
                continue
            }
            instructions +=
                when (instruction.type) {
                    LogicDisplay.commandColor -> {
                        val r = normalizeColorValue(executor.numi(instruction.x))
                        val g = normalizeColorValue(executor.numi(instruction.y))
                        val b = normalizeColorValue(executor.numi(instruction.p1))
                        val a = normalizeColorValue(executor.numi(instruction.p2))
                        NoHornyImage.Instruction.Color(r, g, b, a)
                    }
                    LogicDisplay.commandRect -> {
                        val x = executor.numi(instruction.x)
                        val y = executor.numi(instruction.y)
                        val w = executor.numi(instruction.p1)
                        val h = executor.numi(instruction.p2)
                        NoHornyImage.Instruction.Rect(x, y, w, h)
                    }
                    LogicDisplay.commandTriangle -> {
                        val x1 = executor.numi(instruction.x)
                        val y1 = executor.numi(instruction.y)
                        val x2 = executor.numi(instruction.p1)
                        val y2 = executor.numi(instruction.p2)
                        val x3 = executor.numi(instruction.p3)
                        val y3 = executor.numi(instruction.p4)
                        NoHornyImage.Instruction.Triangle(x1, y1, x2, y2, x3, y3)
                    }
                    else -> continue
                }
        }
        return instructions
    }

    private fun normalizeColorValue(value: Int): Int {
        val result = value % 256
        return if (result < 0) result + 256 else result
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DisplaysTracker::class.java)
    }
}
