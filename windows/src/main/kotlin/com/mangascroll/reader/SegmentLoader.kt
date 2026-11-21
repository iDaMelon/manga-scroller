package com.mangascroll.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
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

class SegmentLoader {
    private val maxBytes = Runtime.getRuntime().maxMemory() / 4

    private class Entry(val bitmap: ImageBitmap, val sample: Int, val bytes: Long)

    private val lock = Any()
    private var used = 0L
    private val cache = LinkedHashMap<String, Entry>(64, 0.75f, true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    private fun sampleFor(srcW: Int, targetWidth: Int): Int {
        var s = 1
        while (srcW / (s * 2) >= targetWidth) s *= 2
        return s
    }

    /** A cached bitmap that is sharp enough for [targetWidth], if any. */
    fun cached(seg: Segment, targetWidth: Int): ImageBitmap? = synchronized(lock) {
        cache[seg.key]?.takeIf { it.sample <= sampleFor(seg.srcW, targetWidth) }?.bitmap
    }

    /** Any cached bitmap (even a blurrier one) to show while a sharper one loads. */
    fun anyCached(seg: Segment): ImageBitmap? = synchronized(lock) { cache[seg.key]?.bitmap }

    suspend fun load(seg: Segment, targetWidth: Int): ImageBitmap? {
        cached(seg, targetWidth)?.let { return it }
        return withContext(dispatcher) {
            cached(seg, targetWidth) ?: run {
                val sample = sampleFor(seg.srcW, targetWidth.coerceAtLeast(1))
                val img = Images.readRegion(seg.file, seg.srcW, seg.top, seg.bottom, sample) ?: return@run null
                val bmp = img.toComposeImageBitmap()
                put(seg.key, Entry(bmp, sample, img.width.toLong() * img.height * 4))
                bmp
            }
        }
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
