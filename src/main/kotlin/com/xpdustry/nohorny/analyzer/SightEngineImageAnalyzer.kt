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

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xpdustry.nohorny.NoHornyConfig
import com.xpdustry.nohorny.extension.toJpgByteArray
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.concurrent.CompletableFuture
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory

internal class SightEngineImageAnalyzer(
    private val config: NoHornyConfig.Analyzer.SightEngine,
    private val gson: Gson,
    private val http: OkHttpClient,
) : ImageAnalyzer {
    override fun analyse(image: BufferedImage): CompletableFuture<ImageAnalyzer.Result> {
        val request =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_user", config.sightEngineUser)
                .addFormDataPart("api_secret", config.sightEngineSecret.value)
                .addFormDataPart(
                    "models",
                    config.kinds.joinToString(",") {
                        when (it) {
                            ImageAnalyzer.Kind.NUDITY -> "nudity-2.0"
                            ImageAnalyzer.Kind.GORE -> "gore"
                        }
                    })
                .addFormDataPart(
                    "media",
                    "image.jpg",
                    image.toJpgByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull(), 0))
                .build()

        val completable = CompletableFuture<ImageAnalyzer.Result>()

        http
            .newCall(
                Request.Builder()
                    .url("https://api.sightengine.com/1.0/check.json")
                    .post(request)
                    .build())
            .enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        val json =
                            response.use {
                                gson.fromJson(response.body!!.charStream(), JsonObject::class.java)
                            }

                        if (json["status"].asString != "success") {
                            completable.completeExceptionally(
                                IOException("SightEngine API returned error: ${json["error"]}"))
                            return
                        }

                        logger.debug("SightEngine response: {}", json)

                        val results = mutableMapOf<ImageAnalyzer.Kind, Float>()

                        if (ImageAnalyzer.Kind.NUDITY in config.kinds) {
                            val percent =
                                EXPLICIT_NUDITY_FIELDS.maxOf {
                                    json["nudity"].asJsonObject[it].asFloat
                                }
                            results[ImageAnalyzer.Kind.NUDITY] = percent
                        }

                        if (ImageAnalyzer.Kind.GORE in config.kinds) {
                            val percent = json["gore"].asJsonObject["prob"].asFloat
                            results[ImageAnalyzer.Kind.GORE] = percent
                        }

                        val result = results.maxOfOrNull { it.value } ?: 0F
                        val rating =
                            when {
                                result > config.unsafeThreshold -> ImageAnalyzer.Rating.UNSAFE
                                result > config.warningThreshold -> ImageAnalyzer.Rating.WARNING
                                else -> ImageAnalyzer.Rating.SAFE
                            }

                        completable.complete(ImageAnalyzer.Result(rating, results))
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        completable.completeExceptionally(
                            IOException(
                                "An error occurred while doing an API request to sight-engine", e))
                    }
                })

        return completable
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SightEngineImageAnalyzer::class.java)
        private val EXPLICIT_NUDITY_FIELDS =
            listOf("sexual_activity", "sexual_display", "sextoy", "erotica")
    }
}
