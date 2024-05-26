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
package com.xpdustry.nohorny.extension

import com.xpdustry.nohorny.NoHornyImage
import com.xpdustry.nohorny.geometry.Cluster
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal fun BufferedImage.toJpgByteArray(): ByteArray {
    var image = this
    if (this.type != BufferedImage.TYPE_INT_RGB) {
        image = BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { drawImage(this@toJpgByteArray, 0, 0, null) }.dispose()
    }
    return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
}

internal fun render(cluster: Cluster<out NoHornyImage>): BufferedImage {
    val image =
        BufferedImage(
            cluster.w * RES_PER_BLOCK, cluster.h * RES_PER_BLOCK, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.graphics
    graphics.color = Color(0, 0, 0, 0)
    graphics.fillRect(0, 0, image.width, image.height)

    for (block in cluster.blocks) {
        graphics.drawImage(
            createImage(block.payload),
            (block.x - cluster.x) * RES_PER_BLOCK,
            (block.y - cluster.y) * RES_PER_BLOCK,
            block.size * RES_PER_BLOCK,
            block.size * RES_PER_BLOCK,
            null,
        )
    }

    // Invert y-axis, because mindustry uses bottom-left as origin
    val inverted = invertYAxis(image)
    graphics.dispose()
    return inverted
}

private fun createImage(image: NoHornyImage): BufferedImage {
    var output = BufferedImage(image.resolution, image.resolution, BufferedImage.TYPE_INT_RGB)
    val graphics = output.graphics
    graphics.color = Color(0, 0, 0, 0)
    graphics.fillRect(0, 0, output.width, output.height)

    when (image) {
        is NoHornyImage.Canvas -> {
            for (pixel in image.pixels) {
                output.setRGB(
                    pixel.key % image.resolution, pixel.key / image.resolution, pixel.value)
            }
            output = invertYAxis(output)
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
                                    instruction.x, instruction.y, instruction.w, instruction.h)
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

    graphics.dispose()
    return output
}

private fun invertYAxis(image: BufferedImage): BufferedImage {
    val transform = AffineTransform.getScaleInstance(1.0, -1.0)
    transform.translate(0.0, -image.getHeight(null).toDouble())
    val op = AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR)
    return op.filter(image, null)
}

private const val RES_PER_BLOCK = 32
