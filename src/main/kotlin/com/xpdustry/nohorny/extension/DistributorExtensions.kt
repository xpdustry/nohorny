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
package com.xpdustry.nohorny.extension

import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.event.EventSubscription
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import fr.xpdustry.distributor.api.scheduler.PluginTask
import fr.xpdustry.distributor.api.util.ArcCollections
import fr.xpdustry.distributor.api.util.Priority
import java.util.function.Consumer
import kotlin.time.Duration
import mindustry.game.EventType
import mindustry.gen.Building
import mindustry.gen.Player
import mindustry.world.blocks.ConstructBlock

internal inline fun <reified T : Building> onBuildingLifecycleEvent(
    plugin: MindustryPlugin,
    crossinline insert: (T, Player?) -> Unit,
    crossinline remove: (Int, Int) -> Unit
) {
    onEvent<EventType.BlockBuildEndEvent>(plugin) { event ->
        var buildings = listOf(event.tile.build)
        if (event.breaking) {
            val constructing = (buildings[0] as? ConstructBlock.ConstructBuild)
            if (constructing?.prevBuild != null) {
                buildings = ArcCollections.immutableList(constructing.prevBuild)
            }
        }

        for (building in buildings) {
            if (building is T) {
                insert(building, event.unit.player)
            }
        }
    }

    onEvent<EventType.ConfigEvent>(plugin) { event ->
        val building = event.tile
        if (event.player != null && building is T) {
            remove(building.rx, building.ry)
            insert(building, event.player)
        }
    }

    onEvent<EventType.BlockDestroyEvent>(plugin) { event ->
        val building = event.tile
        if (building is T) {
            remove(building.rx, building.ry)
        }
    }

    onEvent<EventType.TileChangeEvent>(plugin) { event ->
        val building = event.tile.build
        if (building !is T) {
            remove(event.tile.x.toInt(), event.tile.y.toInt())
        }
    }
}

internal fun schedule(
    plugin: MindustryPlugin,
    async: Boolean,
    delay: Duration? = null,
    repeat: Duration? = null,
    task: Runnable
): PluginTask<Void> {
    val builder =
        if (async) DistributorProvider.get().pluginScheduler.scheduleAsync(plugin)
        else DistributorProvider.get().pluginScheduler.scheduleSync(plugin)
    if (delay != null) builder.delay(delay.inWholeMilliseconds, MindustryTimeUnit.MILLISECONDS)
    if (repeat != null) builder.repeat(repeat.inWholeMilliseconds, MindustryTimeUnit.MILLISECONDS)
    return builder.execute(task)
}

internal inline fun <reified T : Any> onEvent(
    plugin: MindustryPlugin,
    priority: Priority = Priority.NORMAL,
    consumer: Consumer<T>
): EventSubscription =
    DistributorProvider.get().eventBus.subscribe(T::class.java, priority, plugin, consumer)
