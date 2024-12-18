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

import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal inline fun BufferedImage.withGraphics(block: (Graphics2D) -> Unit) {
    val graphics = createGraphics()
    try {
        block(graphics)
    } finally {
        graphics.dispose()
    }
}

internal fun BufferedImage.toJpgByteArray(): ByteArray {
    var image = this
    if (this.type != BufferedImage.TYPE_INT_RGB) {
        image = BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB)
        image.withGraphics { graphics ->
            graphics.drawImage(this@toJpgByteArray, 0, 0, null)
        }
    }
    return ByteArrayOutputStream()
        .also { ImageIO.write(image, "jpg", it) }
        .toByteArray()
}

internal fun BufferedImage.resize(
    w: Int,
    h: Int,
    fill: Color? = null,
): BufferedImage {
    val source =
        if (fill == null) {
            getScaledInstance(w, h, Image.SCALE_SMOOTH)
        } else {
            this
        }
    val result = BufferedImage(w, h, type)
    result.withGraphics { graphics ->
        if (fill != null) {
            graphics.color = fill
            graphics.fillRect(0, 0, w, h)
        }
        graphics.drawImage(source, 0, 0, w, h, null)
    }
    return result
}

internal fun BufferedImage.invertYAxis(): BufferedImage {
    val transform = AffineTransform.getScaleInstance(1.0, -1.0)
    transform.translate(0.0, -getHeight(null).toDouble())
    val op = AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR)
    return op.filter(this, null)
}
