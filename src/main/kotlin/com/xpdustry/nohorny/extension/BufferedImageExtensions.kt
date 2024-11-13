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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal inline fun BufferedImage.withGraphics(block: (Graphics2D) -> Unit): BufferedImage {
    val image = BufferedImage(width, height, type)
    val graphics = image.createGraphics()
    try {
        block(graphics)
    } finally {
        graphics.dispose()
    }
    return image
}

internal fun BufferedImage.toJpgByteArray(): ByteArray {
    var image = this
    if (this.type != BufferedImage.TYPE_INT_RGB) {
        image = BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { drawImage(this@toJpgByteArray, 0, 0, null) }.dispose()
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
    return BufferedImage(w, h, type).withGraphics {
        if (fill != null) {
            it.color = fill
            it.fillRect(0, 0, w, h)
        }
        it.drawImage(source, 0, 0, w, h, null)
    }
}
