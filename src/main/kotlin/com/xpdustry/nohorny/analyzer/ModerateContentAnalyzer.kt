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

import com.xpdustry.nohorny.NoHornyConfig
import com.xpdustry.nohorny.extension.toCompletableFuture
import com.xpdustry.nohorny.extension.toJpgByteArray
import com.xpdustry.nohorny.extension.toJsonObject
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.concurrent.CompletableFuture
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

internal class ModerateContentAnalyzer(
    private val config: NoHornyConfig.Analyzer.ModerateContent,
    private val http: OkHttpClient
) : ImageAnalyzer {
    override fun analyse(image: BufferedImage): CompletableFuture<ImageAnalyzer.Result> =
        http
            .newCall(
                Request.Builder()
                    .url("https://tmpfiles.org/api/v1/upload")
                    .post(
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                "file",
                                "image.jpg",
                                image
                                    .toJpgByteArray()
                                    .toRequestBody("image/jpeg".toMediaTypeOrNull()))
                            .build())
                    .build())
            .toCompletableFuture()
            .thenCompose { response ->
                logger.trace("tmpfiles.org reponse: {}", response)

                val json = response.toJsonObject()
                if (response.code != 200) {
                    return@thenCompose CompletableFuture.failedFuture(
                        IOException("Failed to upload the image: $json"))
                }

                val path = json["data"].asJsonObject["url"].asString.toHttpUrl().pathSegments

                return@thenCompose http
                    .newCall(
                        Request.Builder()
                            .url(
                                "https://api.moderatecontent.com/moderate/"
                                    .toHttpUrl()
                                    .newBuilder()
                                    .addQueryParameter("key", config.moderateContentToken.value)
                                    .addQueryParameter(
                                        "url", "https://tmpfiles.org/dl/${path[0]}/${path[1]}")
                                    .build())
                            .post(byteArrayOf().toRequestBody())
                            .header("Content-Length", "0")
                            .build())
                    .toCompletableFuture()
            }
            .thenApply(Response::toJsonObject)
            .thenCompose { json ->
                logger.debug("API response: {}", json)

                val code = json["error_code"].asInt
                if (code != 0) {
                    return@thenCompose CompletableFuture.failedFuture(
                        IOException(
                            "ModerateContent API returned an error: ${json["error"].asString} ($code)"))
                }

                val prediction =
                    json["predictions"].asJsonObject.asMap().mapValues { it.value.asFloat }

                val result =
                    when (val label = json["rating_label"].asString) {
                        EVERYONE_LABEL ->
                            ImageAnalyzer.Result(
                                ImageAnalyzer.Rating.SAFE,
                                mapOf(ImageAnalyzer.Kind.NUDITY to prediction[EVERYONE_LABEL]!!))
                        TEEN_LABEL ->
                            ImageAnalyzer.Result(
                                ImageAnalyzer.Rating.WARNING,
                                mapOf(ImageAnalyzer.Kind.NUDITY to prediction[TEEN_LABEL]!!))
                        ADULT_LABEL ->
                            ImageAnalyzer.Result(
                                ImageAnalyzer.Rating.UNSAFE,
                                mapOf(ImageAnalyzer.Kind.NUDITY to prediction[ADULT_LABEL]!!))
                        else -> {
                            return@thenCompose CompletableFuture.failedFuture(
                                IOException("Unknown label: $label"))
                        }
                    }

                return@thenCompose CompletableFuture.completedFuture(result)
            }

    companion object {
        private val logger = LoggerFactory.getLogger(ModerateContentAnalyzer::class.java)
        private const val EVERYONE_LABEL = "everyone"
        private const val TEEN_LABEL = "teen"
        private const val ADULT_LABEL = "adult"
    }
}
