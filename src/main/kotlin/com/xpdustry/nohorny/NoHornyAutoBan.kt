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
package com.xpdustry.nohorny

import com.xpdustry.nohorny.extension.onEvent
import com.xpdustry.nohorny.geometry.ImmutablePoint
import com.xpdustry.nohorny.image.NoHornyImage
import com.xpdustry.nohorny.image.NoHornyInformation
import com.xpdustry.nohorny.image.analyzer.ImageAnalyzerEvent
import kotlinx.coroutines.Dispatchers
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.world.blocks.logic.CanvasBlock
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.LogicDisplay

internal class NoHornyAutoBan(private val plugin: NoHornyPlugin) : NoHornyListener("Auto Ban", Dispatchers.Default) {
    override fun onInit() {
        onEvent<ImageAnalyzerEvent> { (result, cluster, _, author) ->
            if (result.rating == NoHornyInformation.Rating.UNSAFE &&
                plugin.config.autoBan &&
                author != null
            ) {
                for (player in Groups.player) {
                    if (player.uuid() == author.uuid || player.ip() == author.address.hostAddress) {
                        Vars.netServer.admins.banPlayer(player.uuid())
                        player.kick("[scarlet]You have been banned for building a NSFW building.")
                        Call.sendMessage(
                            "[pink]NoHorny: [white]${player.plainName()} has been banned for building a NSFW building.",
                        )
                    }
                }
                cluster.blocks
                    .asSequence()
                    .flatMap { block ->
                        val points = mutableListOf(ImmutablePoint(block.x, block.y))
                        if (block.data is NoHornyImage.Display) {
                            points += block.data.processors.keys
                        }
                        points.asSequence()
                    }
                    .forEach { point ->
                        val building = Vars.world.build(point.x, point.y)
                        if (building is LogicDisplay.LogicDisplayBuild ||
                            building is LogicBlock.LogicBuild ||
                            building is CanvasBlock.CanvasBuild
                        ) {
                            Vars.world.tile(point.x, point.y)?.setNet(Blocks.air)
                        }
                    }
            }
        }
    }
}
