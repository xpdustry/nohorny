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
package com.xpdustry.nohorny

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.struct.IntIntMap
import arc.struct.IntSet
import arc.util.CommandHandler
import com.xpdustry.nohorny.analyzer.ImageAnalyzerEvent
import com.xpdustry.nohorny.extension.*
import com.xpdustry.nohorny.extension.rx
import com.xpdustry.nohorny.extension.ry
import com.xpdustry.nohorny.geometry.Cluster
import com.xpdustry.nohorny.geometry.ClusterManager
import com.xpdustry.nohorny.geometry.ImmutablePoint
import java.net.InetAddress
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.logic.LExecutor
import mindustry.logic.LExecutor.DrawFlushI
import mindustry.world.blocks.logic.CanvasBlock
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.LogicDisplay

internal class NoHornyTracker(private val plugin: NoHornyPlugin) : NoHornyListener {

    private val processors = mutableMapOf<ImmutablePoint, ProcessorWithLinks>()
    private val displays = ClusterManager.create<NoHornyImage.Display>()
    private val displayProcessingQueue = PriorityQueue<ProcessingTask>()
    private val canvases = ClusterManager.create<NoHornyImage.Canvas>()
    private val canvasProcessingQueue = PriorityQueue<ProcessingTask>()
    private val debug = IntSet()

    override fun onInit() {
        onBuildingLifecycleEvent(::addDisplay, ::removeDisplay)
        onBuildingLifecycleEvent(::addProcessor, ::removeProcessor)
        onBuildingLifecycleEvent(::addCanvas, ::removeCanvas)

        onEvent<EventType.ResetEvent> {
            processors.clear()
            displays.clear()
            displayProcessingQueue.clear()
            canvases.clear()
            canvasProcessingQueue.clear()
            NoHornyLogger.trace("Reset tracker")
        }

        startProcessing(displays, displayProcessingQueue, "display")
        startProcessing(canvases, canvasProcessingQueue, "canvas")

        onEvent<EventType.PlayerLeave> { event -> debug.remove(event.player.id) }
        schedule(async = false, repeat = 1.seconds) {
            debug.each { id ->
                val player = Groups.player.getByID(id) ?: return@each
                renderCluster(player, displays, Color.pink)
                renderCluster(player, canvases, Color.orange)
            }
        }
    }

    private fun renderCluster(player: Player, manager: ClusterManager<*>, color: Color) {
        for (cluster in manager.clusters) {
            for (block in cluster.blocks) {
                Call.label(
                    player.con,
                    "[#$color]C${cluster.identifier}",
                    1F,
                    block.x.toFloat() * Vars.tilesize,
                    block.y.toFloat() * Vars.tilesize,
                )
                val data = block.payload
                if (data is NoHornyImage.Display) {
                    for (processor in data.processors.keys) {
                        Call.label(
                            player.con,
                            "[#$color]C${cluster.identifier}P",
                            1F,
                            processor.x.toFloat() * Vars.tilesize,
                            processor.y.toFloat() * Vars.tilesize,
                        )
                    }
                }
            }
        }
    }

    override fun onClientCommandsRegistration(handler: CommandHandler) {
        handler.register<Player>(
            "nohorny-tracker-debug", "Enable debug mode of the nohorny tracker.") { _, player ->
                if (debug.add(player.id)) {
                    player.sendMessage("Tracker debug mode is now enabled")
                } else {
                    debug.remove(player.id)
                    player.sendMessage("Tracker debug mode is now disabled")
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : NoHornyImage> startProcessing(
        manager: ClusterManager<T>,
        queue: PriorityQueue<ProcessingTask>,
        name: String
    ) =
        schedule(async = false, delay = 1.seconds, repeat = 1.seconds) {
            val element = queue.peek()
            if (element == null || element.instant > Instant.now()) return@schedule
            queue.remove()
            val cluster = manager.getClusterByIdentifier(element.identifier) ?: return@schedule
            val copy =
                cluster.copy(
                    blocks = cluster.blocks.map { it.copy(payload = it.payload.copy() as T) })

            NoHornyLogger.trace("Begin processing of {} cluster {}", name, copy.identifier)

            schedule(async = true) {
                val image = render(copy)
                plugin.cache
                    .getResult(copy, image)
                    .thenCompose { cached ->
                        if (cached != null) {
                            CompletableFuture.completedFuture(cached to true)
                        } else {
                            plugin.analyzer.analyse(image).thenApply { it to false }
                        }
                    }
                    .orTimeout(10L, TimeUnit.SECONDS)
                    .whenComplete { (result, cached), error ->
                        if (error != null) {
                            NoHornyLogger.error(
                                "Failed to verify cluster {}", copy.identifier, error)
                            return@whenComplete
                        }

                        if (!cached) {
                            plugin.cache.putResult(copy, image, result)
                        }

                        NoHornyLogger.trace(
                            "Processed {} cluster {}, posting event", name, copy.identifier)
                        Core.app.post { Events.fire(ImageAnalyzerEvent(result, copy, image)) }
                    }
            }
        }

    private fun addCanvas(canvas: CanvasBlock.CanvasBuild, player: Player?) {
        handleCanvasesModifications(
            canvases.addBlock(
                Cluster.Block(
                    canvas.rx,
                    canvas.ry,
                    canvas.block.size,
                    NoHornyImage.Canvas(
                        (canvas.block as CanvasBlock).canvasSize,
                        readCanvas(canvas),
                        player?.asAuthor()))))
    }

    private fun removeCanvas(x: Int, y: Int) {
        handleCanvasesModifications(canvases.removeBlock(x, y))
    }

    private fun handleCanvasesModifications(modifications: Iterable<Int>) {
        for (modified in modifications) {
            if (canvasProcessingQueue.removeIf { it.identifier == modified }) {
                NoHornyLogger.trace("Cancelled processing of canvas cluster {}", modified)
            }
            val cluster = canvases.getClusterByIdentifier(modified) ?: return
            if (cluster.blocks.size >= plugin.config.minimumCanvasClusterSize) {
                NoHornyLogger.trace("Scheduled processing of canvas cluster {}", modified)
                canvasProcessingQueue.add(
                    ProcessingTask(
                        modified,
                        Instant.now()
                            .plusMillis(plugin.config.processingDelay.inWholeMilliseconds)))
            }
        }
    }

    private fun addDisplay(display: LogicDisplay.LogicDisplayBuild, player: Player?) {
        val resolution = (display.block as LogicDisplay).displaySize
        val map = mutableMapOf<ImmutablePoint, NoHornyImage.Processor>()
        val block =
            Cluster.Block(
                display.rx, display.ry, display.block.size, NoHornyImage.Display(resolution, map))

        for ((position, data) in processors) {
            if (display.within(
                position.x.toFloat() * Vars.tilesize,
                position.y.toFloat() * Vars.tilesize,
                plugin.config.processorSearchRadius.toFloat() * Vars.tilesize)) {

                val (processor, links) = data
                for (link in links) {
                    if (block.contains(link.x, link.y)) {
                        map[position] = processor
                    }
                }
            }
        }

        handleDisplaysModifications(displays.addBlock(block))
    }

    private fun removeDisplay(x: Int, y: Int) {
        handleDisplaysModifications(displays.removeBlock(x, y))
    }

    private fun handleDisplaysModifications(modifications: Iterable<Int>) {
        for (modified in modifications) {
            if (displayProcessingQueue.removeIf { it.identifier == modified }) {
                NoHornyLogger.trace("Cancelled processing of display cluster {}", modified)
            }
            val cluster = displays.getClusterByIdentifier(modified) ?: return
            if (cluster.blocks.sumOf { it.payload.processors.size } >=
                plugin.config.minimumProcessorCount) {
                NoHornyLogger.trace("Scheduled processing of display cluster {}", modified)
                displayProcessingQueue.add(
                    ProcessingTask(
                        modified,
                        Instant.now()
                            .plusMillis(plugin.config.processingDelay.inWholeMilliseconds)))
            }
        }
    }

    private fun addProcessor(processor: LogicBlock.LogicBuild, player: Player?) {
        if (processor.executor.instructions.any { it is DrawFlushI }) {
            val instructions = readInstructions(processor.executor)
            val links = processor.links.select { it.active }.map { ImmutablePoint(it.x, it.y) }

            if (instructions.size >= plugin.config.minimumInstructionCount && !links.isEmpty) {
                val data =
                    ProcessorWithLinks(
                        NoHornyImage.Processor(instructions, player?.asAuthor()), links.list())

                val point = ImmutablePoint(processor.tileX(), processor.tileY())
                processors[point] = data
                for (link in links) {
                    val element = displays.getBlock(link.x, link.y) ?: continue
                    element.payload.processors[point] = data.first
                }
            }
        }
    }

    private fun removeProcessor(x: Int, y: Int) {
        val point = ImmutablePoint(x, y)
        processors.remove(point)
        val modified = mutableSetOf<Int>()
        for (cluster in displays.clusters) {
            for (block in cluster.blocks) {
                if (block.payload.processors.remove(point) != null) {
                    modified += cluster.identifier
                }
            }
        }
        handleDisplaysModifications(modified)
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
    private fun getByte(block: CanvasBlock, data: ByteArray, bitOffset: Int): Int {
        var result = 0
        for (i in 0 until block.bitsPerPixel) {
            val word = i + bitOffset ushr 3
            result =
                result or
                    ((if (data[word].toInt() and (1 shl (i + bitOffset and 7)) == 0) 0 else 1) shl
                        i)
        }
        return result
    }

    private fun Player.asAuthor() = NoHornyImage.Author(uuid(), InetAddress.getByName(ip()))

    data class ProcessingTask(val identifier: Int, val instant: Instant) :
        Comparable<ProcessingTask> {
        override fun compareTo(other: ProcessingTask): Int = instant.compareTo(other.instant)
    }
}

private typealias ProcessorWithLinks = Pair<NoHornyImage.Processor, List<ImmutablePoint>>
