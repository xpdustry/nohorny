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
package com.xpdustry.nohorny.analyzer

import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture

public interface ImageAnalyzer {

    public fun analyse(image: BufferedImage): CompletableFuture<Result>

    public object None : ImageAnalyzer {
        override fun analyse(image: BufferedImage): CompletableFuture<Result> =
            CompletableFuture.completedFuture(Result.EMPTY)
    }

    public data class Result(val rating: Rating, val details: Map<Kind, Float>) {
        public companion object {
            @JvmField public val EMPTY: Result = Result(Rating.SAFE, emptyMap())
        }
    }

    public enum class Kind {
        NUDITY,
        GORE
    }

    public enum class Rating {
        SAFE,
        WARNING,
        UNSAFE
    }
}
