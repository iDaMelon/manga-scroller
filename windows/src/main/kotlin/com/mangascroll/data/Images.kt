package com.mangascroll.data

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.spi.IIORegistry
import javax.imageio.stream.ImageInputStream

/** Image helpers built on ImageIO (JPEG, PNG, GIF, BMP built in; WebP via TwelveMonkeys). */
object Images {
    init {
        // register plugins explicitly so they also work inside a single merged jar
        val registry = IIORegistry.getDefaultInstance()
        runCatching { registry.registerServiceProvider(com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi()) }
        runCatching { registry.registerServiceProvider(com.twelvemonkeys.imageio.plugins.bmp.BMPImageReaderSpi()) }
        ImageIO.setUseCache(false)
    }

    fun ensureLoaded() = Unit

    private inline fun <T> withReader(file: File, block: (ImageReader) -> T): T? {
        val stream: ImageInputStream = ImageIO.createImageInputStream(file) ?: return null
        stream.use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            return try {
                reader.setInput(input, true, true)
                block(reader)
            } finally {
                reader.dispose()
            }
        }
    }

    fun size(file: File): Pair<Int, Int>? = runCatching {
        withReader(file) { it.getWidth(0) to it.getHeight(0) }
    }.getOrNull()?.takeIf { it.first > 0 && it.second > 0 }

    /** Reads rows [top, bottom) of the image, downsampled by [sample]. */
    fun readRegion(file: File, w: Int, top: Int, bottom: Int, sample: Int): BufferedImage? = runCatching {
        withReader(file) { reader ->
            val p: ImageReadParam = reader.defaultReadParam
            p.sourceRegion = Rectangle(0, top, w, bottom - top)
            if (sample > 1) p.setSourceSubsampling(sample, sample, 0, 0)
            reader.read(0, p)
        }
    }.getOrNull()

    fun toRgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_RGB) return src
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, out.width, out.height)
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return out
    }
}
