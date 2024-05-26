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
import arc.util.CommandHandler
import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addPathSource
import com.xpdustry.nohorny.analyzer.DebugImageAnalyzer
import com.xpdustry.nohorny.analyzer.FallbackAnalyzer
import com.xpdustry.nohorny.analyzer.ImageAnalyzer
import com.xpdustry.nohorny.analyzer.ModerateContentAnalyzer
import com.xpdustry.nohorny.analyzer.SightEngineAnalyzer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.notExists
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import mindustry.Vars
import mindustry.mod.Plugin
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

public class NoHornyPlugin : Plugin(), NoHornyAPI {

    private val directory: Path
        get() = Vars.modDirectory.child("nohorny").file().toPath()

    private val file = directory.resolve("config.yaml")

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
            .dispatcher(Dispatcher(EXECUTOR))
            .build()

    private var listeners = mutableListOf<NoHornyListener>()
    internal var config = NoHornyConfig()
    internal var analyzer: ImageAnalyzer = ImageAnalyzer.None
    internal var cache: NoHornyCache = NoHornyCache.None

    init {
        INSTANCE = this
        Files.createDirectories(directory)
        listeners += NoHornyTracker(this)
        listeners += NoHornyAutoBan(this)
    }

    override fun init() {
        reload()
        listeners.forEach(NoHornyListener::onInit)
        NoHornyLogger.info("Initialized nohorny, to the horny jail we go.")

        Core.app.addListener(
            object : ApplicationListener {
                override fun dispose() {
                    EXECUTOR.shutdown()
                    if (!EXECUTOR.awaitTermination(10L, TimeUnit.SECONDS)) {
                        EXECUTOR.shutdownNow()
                    }
                }
            })
    }

    override fun registerServerCommands(handler: CommandHandler) {
        listeners.forEach { it.onServerCommandsRegistration(handler) }
        handler.register<Unit>("nohorny-reload", "Reload nohorny config.") { _, _ ->
            try {
                reload()
                NoHornyLogger.info("Reloaded config")
            } catch (e: ConfigException) {
                NoHornyLogger.error(e.message!!)
            } catch (e: Exception) {
                NoHornyLogger.error("Failed to reload config", e)
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
        NoHornyLogger.debug("Set cache to $cache")
    }

    private fun createAnalyzer(config: NoHornyConfig.Analyzer): ImageAnalyzer =
        when (config) {
            is NoHornyConfig.Analyzer.None -> ImageAnalyzer.None
            is NoHornyConfig.Analyzer.Debug -> DebugImageAnalyzer(directory.resolve("debug"))
            is NoHornyConfig.Analyzer.ModerateContent -> ModerateContentAnalyzer(config, http)
            is NoHornyConfig.Analyzer.SightEngine -> SightEngineAnalyzer(config, http)
            is NoHornyConfig.Analyzer.Fallback ->
                FallbackAnalyzer(createAnalyzer(config.primary), createAnalyzer(config.secondary))
        }

    private object NoHornyThreadFactory : ThreadFactory {
        private val count = AtomicInteger(0)

        override fun newThread(runnable: Runnable) =
            Thread(runnable, "nohorny-worker-${count.incrementAndGet()}").apply { isDaemon = true }
    }

    internal companion object {
        @JvmStatic internal lateinit var INSTANCE: NoHornyPlugin
        @JvmStatic internal val EXECUTOR = Executors.newScheduledThreadPool(4, NoHornyThreadFactory)
    }
}
