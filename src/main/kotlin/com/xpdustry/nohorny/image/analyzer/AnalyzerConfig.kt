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

import com.sksamuel.hoplite.Secret
import com.xpdustry.nohorny.image.NoHornyInformation

internal sealed interface AnalyzerConfig {
    data object None : AnalyzerConfig

    data object Debug : AnalyzerConfig

    data class Fallback(val primary: AnalyzerConfig, val secondary: AnalyzerConfig) : AnalyzerConfig

    data class SightEngine(
        val sightEngineUser: String,
        val sightEngineSecret: Secret,
        val unsafeThreshold: Float = 0.55F,
        val warningThreshold: Float = 0.4F,
        val kinds: List<NoHornyInformation.Kind> = listOf(NoHornyInformation.Kind.NUDITY),
    ) : AnalyzerConfig {
        init {
            require(unsafeThreshold >= 0) { "unsafeThreshold cannot be lower than 0" }
            require(warningThreshold >= 0) { "warningThreshold cannot be lower than 0" }
            require(kinds.isNotEmpty()) { "models cannot be empty" }
        }
    }
}
