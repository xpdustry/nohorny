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

import arc.ApplicationListener
import arc.Core
import arc.Events
import arc.util.CommandHandler
import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addPathSource
import com.xpdustry.nohorny.analyzer.DebugImageAnalyzer
import com.xpdustry.nohorny.analyzer.FallbackAnalyzer
import com.xpdustry.nohorny.analyzer.ImageAnalyzer
import com.xpdustry.nohorny.analyzer.ImageAnalyzerEvent
import com.xpdustry.nohorny.analyzer.SightEngineAnalyzer
import com.xpdustry.nohorny.geometry.BlockGroup
import com.xpdustry.nohorny.tracker.CanvasesTracker
import com.xpdustry.nohorny.tracker.DisplaysTracker
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry.Vars
import mindustry.mod.Plugin
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.lang.Runnable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.notExists
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

public class NoHornyPlugin : Plugin(), NoHornyAPI {
    private val directory: Path = Vars.modDirectory.child("nohorny").file().toPath()
    private val file = directory.resolve("config.yaml")
    private val listeners = mutableListOf<NoHornyListener>()
    private val executor = Executors.newScheduledThreadPool(4, NoHornyThreadFactory)
    private val logger = LoggerFactory.getLogger(NoHornyPlugin::class.java)
    private val renderer: NoHornyImageRenderer = SimpleImageRenderer

    private val loader =
        ConfigLoaderBuilder.empty()
            .withClassLoader(javaClass.classLoader)
            .addDefaultDecoders()
            .addDefaultParamMappers()
            .addDefaultParsers()
            .addPathSource(file)
            .strict()
            .build()

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(10.seconds.toJavaDuration())
            .connectTimeout(10.seconds.toJavaDuration())
            .readTimeout(10.seconds.toJavaDuration())
            .writeTimeout(10.seconds.toJavaDuration())
            .dispatcher(Dispatcher(executor))
            .build()

    internal val coroutines = NoHornyCoroutines(executor)
    internal var config = NoHornyConfig()
    internal var analyzer: ImageAnalyzer = ImageAnalyzer.None
    internal var cache: NoHornyCache = NoHornyCache.None

    init {
        Files.createDirectories(directory)
        listeners += CanvasesTracker(this)
        listeners += DisplaysTracker(this)
        listeners += NoHornyAutoBan(this)
    }

    override fun init() {
        reload()
        listeners.forEach(NoHornyListener::onInit)
        logger.info("Initialized no-horny, to the horny jail we go.")

        Core.app.addListener(
            object : ApplicationListener {
                override fun dispose() {
                    executor.shutdown()
                    if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                        executor.shutdownNow()
                    }
                    coroutines.job.complete()
                    runBlocking { coroutines.job.join() }
                }
            },
        )
    }

    override fun registerServerCommands(handler: CommandHandler) {
        listeners.forEach { it.onServerCommandsRegistration(handler) }
        handler.register<Unit>("nohorny-reload", "Reload nohorny config.") { _, _ ->
            try {
                reload()
                logger.info("Reloaded config")
            } catch (e: ConfigException) {
                logger.error(e.message!!)
            } catch (e: Exception) {
                logger.error("Failed to reload config", e)
            }
        }
    }

    override fun registerClientCommands(handler: CommandHandler) {
        listeners.forEach { it.onClientCommandsRegistration(handler) }
    }

    private fun reload() {
        if (file.notExists()) {
            analyzer = ImageAnalyzer.None
            return
        }

        val config = loader.loadConfigOrThrow<NoHornyConfig>()
        val analyzer = createAnalyzer(config.analyzer)

        this.config = config
        this.analyzer = analyzer
    }

    override fun setCache(cache: NoHornyCache) {
        this.cache = cache
        logger.debug("Set cache to {}", cache)
    }

    private fun createAnalyzer(config: NoHornyConfig.Analyzer): ImageAnalyzer =
        when (config) {
            is NoHornyConfig.Analyzer.None -> ImageAnalyzer.None
            is NoHornyConfig.Analyzer.Debug -> DebugImageAnalyzer(directory.resolve("debug"))
            is NoHornyConfig.Analyzer.SightEngine -> SightEngineAnalyzer(config, http)
            is NoHornyConfig.Analyzer.Fallback -> FallbackAnalyzer(createAnalyzer(config.primary), createAnalyzer(config.secondary))
        }

    internal fun process(group: BlockGroup<out NoHornyImage>) =
        coroutines.global.launch {
            val image = renderer.render(group)
            var store = false
            val result =
                cache.getResult(group, image).await() ?: run {
                    store = true
                    analyzer.analyse(image).await()
                }
            if (store) {
                cache.putResult(group, image, result)
            }
            Core.app.post {
                Events.fire(ImageAnalyzerEvent(result, group, image))
            }
        }

    private object NoHornyThreadFactory : ThreadFactory {
        private val count = AtomicInteger(0)

        override fun newThread(runnable: Runnable) = Thread(runnable, "nohorny-worker-${count.incrementAndGet()}").apply { isDaemon = true }
    }
}
