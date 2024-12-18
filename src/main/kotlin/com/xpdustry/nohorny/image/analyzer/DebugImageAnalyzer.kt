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
package com.xpdustry.nohorny.image.analyzer

import com.xpdustry.nohorny.extension.toJpgByteArray
import com.xpdustry.nohorny.image.NoHornyResult
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.writeBytes

internal class DebugImageAnalyzer(private val directory: Path) : ImageAnalyzer {
    override fun analyse(image: BufferedImage): CompletableFuture<NoHornyResult> {
        try {
            directory.toFile().mkdirs()
            directory
                .resolve("${System.currentTimeMillis()}.jpg")
                .writeBytes(image.toJpgByteArray())
        } catch (error: IOException) {
            return CompletableFuture.failedFuture(error)
        }
        return CompletableFuture.completedFuture(NoHornyResult.EMPTY)
    }
}
