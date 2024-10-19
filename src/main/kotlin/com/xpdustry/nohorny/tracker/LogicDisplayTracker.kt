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

import com.xpdustry.nohorny.NoHornyImage
import com.xpdustry.nohorny.NoHornyListener
import com.xpdustry.nohorny.extension.onBuildingLifecycleEvent
import com.xpdustry.nohorny.extension.rx
import com.xpdustry.nohorny.extension.ry
import com.xpdustry.nohorny.geometry.AdjacencyIndex
import com.xpdustry.nohorny.geometry.ImmutablePoint
import com.xpdustry.nohorny.geometry.IndexBlock
import mindustry.Vars
import mindustry.logic.LExecutor
import mindustry.world.blocks.logic.LogicDisplay

private typealias ProcessorWithLinks = Pair<NoHornyImage.Processor, List<ImmutablePoint>>

internal data class LogicDisplayConfig(
    val processorSearchRadius: Int = 10,
)

internal class LogicDisplayTracker : NoHornyListener {
    private val config = LogicDisplayConfig()
    private val processors = mutableMapOf<ImmutablePoint, ProcessorWithLinks>()
    private val displays = AdjacencyIndex<NoHornyImage.Display>()

    override fun onInit() {
        onBuildingLifecycleEvent<LogicDisplay.LogicDisplayBuild>(
            insert = { display, _, _ ->
                val resolution = (display.block as LogicDisplay).displaySize
                val map = mutableMapOf<ImmutablePoint, NoHornyImage.Processor>()
                val block =
                    IndexBlock(
                        display.rx,
                        display.ry,
                        display.block.size,
                        NoHornyImage.Display(resolution, map),
                    )

                for ((position, data) in processors) {
                    if (display.within(
                            position.x.toFloat() * Vars.tilesize,
                            position.y.toFloat() * Vars.tilesize,
                            config.processorSearchRadius.toFloat() * Vars.tilesize,
                        )
                    ) {
                        val (processor, links) = data
                        for (link in links) {
                            if (block.contains(link.x, link.y)) {
                                map[position] = processor
                            }
                        }
                    }
                }

                displays.upsert(block)
            },
            remove = { x, y ->
                displays.remove(x, y)
            },
        )
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
}
