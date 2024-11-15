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
package com.xpdustry.nohorny.image

import arc.Core
import arc.Events
import com.xpdustry.nohorny.NoHornyListener
import com.xpdustry.nohorny.geometry.IndexGroup
import com.xpdustry.nohorny.image.analyzer.ImageAnalyzer
import com.xpdustry.nohorny.image.analyzer.ImageAnalyzerEvent
import com.xpdustry.nohorny.image.cache.ImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

internal interface ImageProcessor {
    fun process(group: IndexGroup<out NoHornyImage>)
}

internal class ImageProcessorImpl(
    private val analyzer: () -> ImageAnalyzer,
    private val cache: ImageCache,
    private val renderer: ImageRenderer,
) : ImageProcessor, NoHornyListener("Image Processor", Dispatchers.Default) {
    override fun process(group: IndexGroup<out NoHornyImage>) {
        scope.launch {
            logger.trace("Processing group at ({}, {})", group.x, group.y)
            val image = renderer.render(group)
            var store = false
            var result =
                try {
                    cache.getResult(group, image).await()
                } catch (e: Exception) {
                    logger.error("Failed to get cached result for group at (${group.x}, ${group.y})", e)
                    null
                }
            if (result == null) {
                logger.trace("Cache miss for group at ({}, {})", group.x, group.y)
                store = true
                try {
                    result = analyzer().analyse(image).await()!!
                } catch (e: Exception) {
                    logger.error("Failed to analyse image for group at (${group.x}, ${group.y})", e)
                    return@launch
                }
            } else {
                logger.trace("Cache hit for group at ({}, {})", group.x, group.y)
            }

            logger.trace("Result for group at ({}, {}): {}", group.x, group.y, result)
            if (store) {
                logger.trace("Storing result for group at ({}, {})", group.x, group.y)
                cache.putResult(group, image, result)
            }
            Core.app.post {
                Events.fire(ImageAnalyzerEvent(result, group, image))
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ImageProcessorImpl::class.java)
    }
}
