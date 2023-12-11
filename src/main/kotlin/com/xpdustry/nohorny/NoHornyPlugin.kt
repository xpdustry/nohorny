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

import arc.util.CommandHandler
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.Gson
import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addPathSource
import com.xpdustry.nohorny.analyzer.DebugImageAnalyzer
import com.xpdustry.nohorny.analyzer.ImageAnalyzer
import com.xpdustry.nohorny.analyzer.SightEngineImageAnalyzer
import fr.xpdustry.distributor.api.plugin.AbstractMindustryPlugin
import java.util.concurrent.Executors
import kotlin.io.path.notExists
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

public class NoHornyPlugin : AbstractMindustryPlugin() {

    private val file = directory.resolve("config.yaml")
    private val gson = Gson()

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
            .dispatcher(
                Dispatcher(
                    Executors.newFixedThreadPool(
                        2,
                        ThreadFactoryBuilder()
                            .setDaemon(true)
                            .setNameFormat("nohorny-worker-http-%d")
                            .build())))
            .build()

    internal var config = NoHornyConfig()
    internal var analyzer: ImageAnalyzer = ImageAnalyzer.None

    override fun onInit() {
        reload()
        addListener(NoHornyTracker(this))
        addListener(NoHornyAutoBan(this))
        logger.info("Initialized nohorny, to the horny jail we go.")
    }

    override fun onServerCommandsRegistration(handler: CommandHandler) {
        handler.register<Unit>("nohorny-reload", "Reload nohorny config.") { _, _ ->
            try {
                reload()
                logger.info("Reloaded config")
            } catch (e: ConfigException) {
                logger.error(e.message)
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
        val analyzer =
            when (config.analyzer) {
                is NoHornyConfig.Analyzer.None -> ImageAnalyzer.None
                is NoHornyConfig.Analyzer.SightEngine ->
                    SightEngineImageAnalyzer(config.analyzer, gson, http)
                is NoHornyConfig.Analyzer.Debug -> DebugImageAnalyzer(directory.resolve("debug"))
            }

        this.config = config
        this.analyzer = analyzer
    }
}
