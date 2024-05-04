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

import com.sksamuel.hoplite.Secret
import com.xpdustry.nohorny.analyzer.ImageAnalyzer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class NoHornyConfig(
    val analyzer: Analyzer = Analyzer.None,
    val autoBan: Boolean = true,
    val minimumInstructionCount: Int = 100,
    val processingDelay: Duration = 5.seconds,
    val minimumCanvasClusterSize: Int = 9,
    val minimumProcessorCount: Int = 5,
    val processorSearchRadius: Int = 10,
    val alwaysProcess: Boolean = false
) {
    init {
        require(minimumInstructionCount >= 1) { "minimumInstructionCount cannot be lower than 1" }
        require(processingDelay >= Duration.ZERO) { "processingDelay cannot be lower than 0" }
        require(minimumCanvasClusterSize >= 1) { "minimumCanvasClusterSize cannot be lower than 1" }
        require(minimumProcessorCount >= 1) { "minimumProcessorCount cannot be lower than 1" }
        require(processorSearchRadius >= 1) { "processorSearchRadius cannot be lower than 1" }
    }

    sealed interface Analyzer {
        data object None : Analyzer

        data object Debug : Analyzer

        data class SightEngine(
            val sightEngineUser: String,
            val sightEngineSecret: Secret,
            val unsafeThreshold: Float = 0.55F,
            val warningThreshold: Float = 0.4F,
            val kinds: List<ImageAnalyzer.Kind> = listOf(ImageAnalyzer.Kind.NUDITY)
        ) : Analyzer {
            init {
                require(unsafeThreshold >= 0) { "unsafeThreshold cannot be lower than 0" }
                require(warningThreshold >= 0) { "warningThreshold cannot be lower than 0" }
                require(kinds.isNotEmpty()) { "models cannot be empty" }
            }
        }

        data class ModerateContent(val moderateContentToken: Secret) : Analyzer
    }
}
