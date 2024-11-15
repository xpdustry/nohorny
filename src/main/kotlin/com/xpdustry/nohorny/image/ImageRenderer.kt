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

import com.xpdustry.nohorny.extension.invertYAxis
import com.xpdustry.nohorny.extension.withGraphics
import com.xpdustry.nohorny.geometry.IndexGroup
import java.awt.Color
import java.awt.image.BufferedImage

internal interface ImageRenderer {
    fun render(group: IndexGroup<out NoHornyImage>): BufferedImage
}

internal object ImageRendererImpl : ImageRenderer {
    private const val PIXELS_PER_BLOCK = 32

    override fun render(group: IndexGroup<out NoHornyImage>): BufferedImage {
        val image = BufferedImage(group.w * PIXELS_PER_BLOCK, group.h * PIXELS_PER_BLOCK, BufferedImage.TYPE_INT_ARGB)
        image.withGraphics { graphics ->
            graphics.color = Color(0, 0, 0, 0)
            graphics.fillRect(0, 0, image.width, image.height)

            for (block in group.blocks) {
                graphics.drawImage(
                    createImage(block.data),
                    (block.x - group.x) * PIXELS_PER_BLOCK,
                    (block.y - group.y) * PIXELS_PER_BLOCK,
                    block.size * PIXELS_PER_BLOCK,
                    block.size * PIXELS_PER_BLOCK,
                    null,
                )
            }
        }
        // Invert y-axis, because mindustry uses bottom-left as origin
        return image.invertYAxis()
    }

    private fun createImage(image: NoHornyImage): BufferedImage {
        val output = BufferedImage(image.resolution, image.resolution, BufferedImage.TYPE_INT_RGB)
        var invert = false
        output.withGraphics { graphics ->
            graphics.color = Color(0, 0, 0, 0)
            graphics.fillRect(0, 0, output.width, output.height)

            when (image) {
                is NoHornyImage.Canvas -> {
                    invert = true
                    for (pixel in image.pixels) {
                        output.setRGB(
                            pixel.key % image.resolution,
                            pixel.key / image.resolution,
                            pixel.value,
                        )
                    }
                }

                is NoHornyImage.Display -> {
                    for (processor in image.processors.values) {
                        for (instruction in processor.instructions) {
                            when (instruction) {
                                is NoHornyImage.Instruction.Color -> {
                                    graphics.color =
                                        Color(instruction.r, instruction.g, instruction.b, instruction.a)
                                }

                                is NoHornyImage.Instruction.Rect -> {
                                    if (instruction.w == 1 && instruction.h == 1) {
                                        output.setRGB(instruction.x, instruction.y, graphics.color.rgb)
                                    } else {
                                        graphics.fillRect(
                                            instruction.x,
                                            instruction.y,
                                            instruction.w,
                                            instruction.h,
                                        )
                                    }
                                }

                                is NoHornyImage.Instruction.Triangle -> {
                                    graphics.fillPolygon(
                                        intArrayOf(instruction.x1, instruction.x2, instruction.x3),
                                        intArrayOf(instruction.y1, instruction.y2, instruction.y3),
                                        3,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return output.let { if (invert) it.invertYAxis() else it }
    }
}
