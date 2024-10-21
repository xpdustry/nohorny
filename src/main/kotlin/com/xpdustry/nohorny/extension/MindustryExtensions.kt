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
package com.xpdustry.nohorny.extension

import arc.Events
import com.google.common.net.InetAddresses
import com.xpdustry.nohorny.NoHornyImage
import com.xpdustry.nohorny.NoHornyPlugin
import mindustry.game.EventType
import mindustry.gen.Building
import mindustry.gen.Player
import mindustry.world.blocks.ConstructBlock
import org.slf4j.LoggerFactory
import java.util.function.Consumer
import kotlin.reflect.jvm.jvmName

internal val Building.rx: Int
    get() = tileX() + block.sizeOffset

internal val Building.ry: Int
    get() = tileY() + block.sizeOffset

internal inline fun <reified T : Building> onBuildingLifecycleEvent(
    crossinline insert: (data: T, player: Player?, isNew: Boolean) -> Unit,
    crossinline remove: (x: Int, y: Int) -> Unit,
) {
    onEvent<EventType.BlockBuildEndEvent> { event ->
        var buildings = listOf(event.tile.build)
        if (event.breaking) {
            val constructing = (buildings[0] as? ConstructBlock.ConstructBuild)
            if (constructing?.prevBuild != null) {
                buildings = constructing.prevBuild.list()
            }
        }

        for (building in buildings) {
            if (building is T) {
                insert(building, event.unit?.player, true)
            }
        }
    }

    onEvent<EventType.ConfigEvent> { event ->
        val building = event.tile
        if (event.player != null && building is T) {
            remove(building.rx, building.ry)
            insert(building, event.player, false)
        }
    }

    onEvent<EventType.BlockDestroyEvent> { event ->
        val building = event.tile
        if (building is T) {
            remove(building.rx, building.ry)
        }
    }

    onEvent<EventType.TileChangeEvent> { event ->
        val building = event.tile.build
        if (building !is T) {
            remove(event.tile.x.toInt(), event.tile.y.toInt())
        }
    }
}

internal inline fun <reified T : Any> onEvent(consumer: Consumer<T>) =
    Events.on(T::class.java) { event ->
        try {
            consumer.accept(event)
        } catch (e: Throwable) {
            LoggerFactory.getLogger(NoHornyPlugin::class.java).error(
                "An error occurred while handling event {}",
                event::class.jvmName,
                e,
            )
        }
    }

internal fun Player.asAuthor() = NoHornyImage.Author(uuid(), InetAddresses.forString(ip()))
