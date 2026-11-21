package com.mangascroll.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One horizontal slice of a page. Long strips get cut into slices no taller than
 * 2x their width, so no single bitmap is ever too big for the GPU or memory.
 * Slices sit edge-to-edge in the list, so pages look like one continuous image.
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
            // avoid a tiny sliver at the end of a page
            if (h - bottom in 1 until maxH / 4) bottom = h
            out += Segment(i, file, w, h, top, bottom)
            top = bottom
        }
    }
    return out to starts
}

class SegmentLoader {
    private val maxBytes = (Runtime.getRuntime().maxMemory() / 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private val decoderLock = Any()
    private val decoders = object : LinkedHashMap<String, BitmapRegionDecoder>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BitmapRegionDecoder>): Boolean {
            if (size > 4) {
                synchronized(eldest.value) { eldest.value.recycle() }
                return true
            }
            return false
        }
    }
    private val unsupported = HashSet<String>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    fun cached(seg: Segment): Bitmap? = cache.get(seg.key)

    suspend fun load(seg: Segment, targetWidth: Int): Bitmap? {
        cache.get(seg.key)?.let { return it }
        return withContext(dispatcher) {
            cache.get(seg.key) ?: runCatching { decode(seg, targetWidth) }.getOrNull()?.also { cache.put(seg.key, it) }
        }
    }

    private fun sampleFor(srcW: Int, targetWidth: Int): Int {
        var s = 1
        while (srcW / (s * 2) >= targetWidth) s *= 2
        return s
    }

    private fun decoderFor(path: String): BitmapRegionDecoder? = synchronized(decoderLock) {
        if (path in unsupported) return null
        decoders[path]?.takeIf { !it.isRecycled } ?: runCatching {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(path, false)
        }.getOrNull().also {
            if (it == null) unsupported += path else decoders[path] = it
        }
    }

    private fun decode(seg: Segment, targetWidth: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(seg.srcW, targetWidth.coerceAtLeast(1))
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        repeat(2) {
            val dec = decoderFor(seg.file.path) ?: return decodeFallback(seg, opts)
            synchronized(dec) {
                if (!dec.isRecycled) {
                    return dec.decodeRegion(Rect(0, seg.top, seg.srcW, seg.bottom), opts)
                }
            }
        }
        return decodeFallback(seg, opts)
    }

    /** For formats the region decoder can't read (GIF, BMP...): decode whole, then crop. */
    private fun decodeFallback(seg: Segment, opts: BitmapFactory.Options): Bitmap? {
        val full = BitmapFactory.decodeFile(seg.file.path, opts) ?: return null
        val s = opts.inSampleSize
        val top = (seg.top / s).coerceIn(0, full.height - 1)
        val h = ((seg.bottom - seg.top) / s).coerceIn(1, full.height - top)
        return if (top == 0 && h == full.height) full else Bitmap.createBitmap(full, 0, top, full.width, h)
    }

    fun close() {
        synchronized(decoderLock) {
            decoders.values.forEach { synchronized(it) { it.recycle() } }
            decoders.clear()
        }
        cache.evictAll()
    }
}
