package com.mangascroll.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt
import com.mangascroll.data.Images
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One horizontal slice of a page. Long strips are cut into slices no taller than
 * 2x their width; slices sit edge-to-edge so pages read as one continuous image.
 */
data class Segment(
    val pageIndex: Int,
    val file: File,
    val srcW: Int,
    val srcH: Int,
    val top: Int,
    val bottom: Int,
) {
    val key: String get() = "${file.path}#$top"
    val ratio: Float get() = srcW.toFloat() / (bottom - top)
}

fun buildSegments(pages: List<Triple<File, Int, Int>>): Pair<List<Segment>, IntArray> {
    val out = ArrayList<Segment>()
    val starts = IntArray(pages.size)
    pages.forEachIndexed { i, (file, w, h) ->
        starts[i] = out.size
        val maxH = (w * 2).coerceAtLeast(1)
        var top = 0
        while (top < h) {
            var bottom = minOf(h, top + maxH)
            if (h - bottom in 1 until maxH / 4) bottom = h
            out += Segment(i, file, w, h, top, bottom)
            top = bottom
        }
    }
    return out to starts
}

/**
 * Decodes a page once and draws all of its slices at exactly the on-screen width,
 * with a cubic (Mitchell) filter: sharper than plain bilinear stretching.
 */
class SegmentLoader(private val segments: List<Segment>) {
    private val maxBytes = Runtime.getRuntime().maxMemory() / 4

    private class Entry(val bitmap: ImageBitmap, val width: Int, val bytes: Long)

    private val lock = Any()
    private var used = 0L
    private val cache = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private val byPage: Map<Int, List<Segment>> = segments.groupBy { it.pageIndex }
    private val pageLocks = HashMap<Int, Any>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    /** A cached bitmap made for exactly [targetWidth], if any. */
    fun cached(seg: Segment, targetWidth: Int): ImageBitmap? = synchronized(lock) {
        cache[seg.key]?.takeIf { it.width == targetWidth }?.bitmap
    }

    /** Any cached bitmap (maybe for another zoom level) to show while the right one loads. */
    fun anyCached(seg: Segment): ImageBitmap? = synchronized(lock) { cache[seg.key]?.bitmap }

    suspend fun load(seg: Segment, targetWidth: Int): ImageBitmap? {
        cached(seg, targetWidth)?.let { return it }
        return withContext(dispatcher) {
            val pageLock = synchronized(lock) { pageLocks.getOrPut(seg.pageIndex) { Any() } }
            synchronized(pageLock) {
                cached(seg, targetWidth) ?: run {
                    runCatching { decodePage(seg, targetWidth) }
                    cached(seg, targetWidth)
                }
            }
        }
    }

    private fun decodePage(seg: Segment, targetWidth: Int) {
        val pageSegs = byPage[seg.pageIndex] ?: listOf(seg)
        val tw = targetWidth.coerceAtLeast(1)
        val image = runCatching { Image.makeFromEncoded(seg.file.readBytes()) }.getOrNull()
        if (image == null) {
            // formats Skia can't read: fall back to ImageIO for this slice only
            val sample = generateSequence(1) { it * 2 }.takeWhile { seg.srcW / (it * 2) >= tw }.last()
            Images.readRegion(seg.file, seg.srcW, seg.top, seg.bottom, sample)?.let { img ->
                put(seg.key, Entry(img.toComposeImageBitmap(), tw, img.width.toLong() * img.height * 4))
            }
            return
        }
        val scale = tw.toFloat() / image.width
        val sampling: SamplingMode = if (scale < 0.5f) FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR) else SamplingMode.MITCHELL
        // the file may differ slightly from the recorded size; map rows proportionally
        val rowScale = image.height.toFloat() / seg.srcH
        for (s in pageSegs) {
            val th = ((s.bottom - s.top) * scale * rowScale).roundToInt().coerceAtLeast(1)
            val surface = Surface.makeRasterN32Premul(tw, th)
            surface.canvas.drawImageRect(
                image,
                Rect.makeLTRB(0f, s.top * rowScale, image.width.toFloat(), s.bottom * rowScale),
                Rect.makeWH(tw.toFloat(), th.toFloat()),
                sampling, null, false,
            )
            val snap = surface.makeImageSnapshot()
            put(s.key, Entry(snap.toComposeImageBitmap(), tw, tw.toLong() * th * 4))
            surface.close()
        }
        image.close()
    }

    private fun put(key: String, e: Entry) = synchronized(lock) {
        cache.remove(key)?.let { used -= it.bytes }
        cache[key] = e
        used += e.bytes
        val it = cache.entries.iterator()
        while (used > maxBytes && it.hasNext()) {
            val old = it.next()
            if (old.key == key) continue
            used -= old.value.bytes
            it.remove()
        }
    }

    fun close() = synchronized(lock) { cache.clear(); used = 0 }
}
