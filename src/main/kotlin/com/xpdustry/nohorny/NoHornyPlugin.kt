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
import arc.util.OS
import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.KebabCaseParamMapper
import com.sksamuel.hoplite.addPathSource
import com.xpdustry.nohorny.image.ImageProcessorImpl
import com.xpdustry.nohorny.image.ImageRendererImpl
import com.xpdustry.nohorny.image.analyzer.AnalyzerConfig
import com.xpdustry.nohorny.image.analyzer.DebugImageAnalyzer
import com.xpdustry.nohorny.image.analyzer.FallbackAnalyzer
import com.xpdustry.nohorny.image.analyzer.ImageAnalyzer
import com.xpdustry.nohorny.image.analyzer.SightEngineAnalyzer
import com.xpdustry.nohorny.image.cache.H2ImageCache
import com.xpdustry.nohorny.image.cache.ImageCache
import com.xpdustry.nohorny.image.cache.ImageCacheConfig
import com.xpdustry.nohorny.tracker.CanvasesTracker
import com.xpdustry.nohorny.tracker.DisplaysTracker
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mindustry.Vars
import mindustry.mod.Plugin
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.lang.Runnable
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

public class NoHornyPlugin : Plugin() {
    private val directory = Vars.modDirectory.child("nohorny").file().toPath()
    private val file = directory.resolve("config.yaml")
    private val listeners = mutableListOf<NoHornyListener>()
    private val executor = Executors.newScheduledThreadPool(4, NoHornyThreadFactory)
    private val logger = LoggerFactory.getLogger(NoHornyPlugin::class.java)

    private val loader =
        ConfigLoaderBuilder.empty()
            .withClassLoader(javaClass.classLoader)
            .addDefaultDecoders()
            .addDefaultParamMappers()
            .addDefaultParsers()
            .addParameterMapper(KebabCaseParamMapper)
            .addPathSource(file)
            .withReport()
            .withReportPrintFn(logger::debug)
            .strict()
            .build()

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(10.seconds.toJavaDuration())
            .connectTimeout(10.seconds.toJavaDuration())
            .readTimeout(10.seconds.toJavaDuration())
            .writeTimeout(10.seconds.toJavaDuration())
            .dispatcher(Dispatcher(Executors.newScheduledThreadPool(4, NoHornyThreadFactory)))
            .build()

    internal var config = NoHornyConfig()
    private var analyzer: ImageAnalyzer = ImageAnalyzer.None

    init {
        Files.createDirectories(directory)
    }

    override fun init() {
        reload()
        val renderer = ImageRendererImpl
        val processor = ImageProcessorImpl({ analyzer }, createCache(config.imageCache), renderer)
        listeners += CanvasesTracker({ config }, processor)
        listeners += DisplaysTracker({ config }, processor)
        listeners += NoHornyAutoMod(this)

        listeners.forEach(NoHornyListener::onInit)
        addExitListener { executor.shutdown() }
        logger.info("Initialized no-horny, to the horny jail we go.")
    }

    override fun registerServerCommands(handler: CommandHandler) {
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

    private fun reload() {
        if (file.notExists()) {
            analyzer = ImageAnalyzer.None
            return
        }
        val config = loader.loadConfigOrThrow<NoHornyConfig>()
        this.config = config
        this.analyzer = createAnalyzer(config.analyzer)
    }

    private fun createAnalyzer(config: AnalyzerConfig): ImageAnalyzer =
        when (config) {
            is AnalyzerConfig.None -> ImageAnalyzer.None
            is AnalyzerConfig.Debug -> DebugImageAnalyzer(directory.resolve("debug"))
            is AnalyzerConfig.Fallback -> FallbackAnalyzer(createAnalyzer(config.primary), createAnalyzer(config.secondary))
            is AnalyzerConfig.SightEngine -> SightEngineAnalyzer(config, http)
        }

    private fun createCache(config: ImageCacheConfig): ImageCache =
        when (config) {
            is ImageCacheConfig.None -> ImageCache.None
            is ImageCacheConfig.Local -> {
                val hikari =
                    HikariDataSource(
                        HikariConfig().apply {
                            jdbcUrl = "jdbc:h2:${directory.resolve("database.h2").absolutePathString()};MODE=MYSQL"
                            poolName = "no-horny-jdbc-pool"
                            maximumPoolSize = OS.cores * 2
                            minimumIdle = 1
                        },
                    )
                addExitListener { hikari.close() }
                H2ImageCache(hikari, config)
            }
        }

    private fun addExitListener(block: () -> Unit) {
        Core.app.listeners.add(
            object : ApplicationListener {
                override fun dispose() = block()

                override fun toString() = "NoHornyExitListener"
            },
        )
    }

    private object NoHornyThreadFactory : ThreadFactory {
        private val count = AtomicInteger(0)

        override fun newThread(runnable: Runnable) =
            Thread(runnable, "nohorny-worker-${count.incrementAndGet()}")
                .apply { isDaemon = true }
    }
}
