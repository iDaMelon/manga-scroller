package com.mangascroll.reader

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-quality resize (Catmull-Rom cubic). Android only offers bilinear filtering when
 * drawing a bitmap, which looks soft when a page is stretched to a bigger screen.
 * When shrinking, the kernel is widened so fine detail doesn't shimmer.
 */
object Resample {

    private fun kernel(x: Float): Float {
        val a = abs(x)
        return when {
            a < 1f -> 1.5f * a * a * a - 2.5f * a * a + 1f
            a < 2f -> -0.5f * a * a * a + 2.5f * a * a - 4f * a + 2f
            else -> 0f
        }
    }

    /** For each output position: first source index and normalized weights. */
    private class Taps(val start: IntArray, val weights: Array<FloatArray>)

    private fun taps(srcLen: Int, dstLen: Int): Taps {
        val scale = dstLen.toFloat() / srcLen
        val stretch = max(1f, 1f / scale) // widen when downscaling
        val support = 2f * stretch
        val n = ceil(support * 2).toInt() + 1
        val start = IntArray(dstLen)
        val weights = Array(dstLen) { FloatArray(n) }
        for (i in 0 until dstLen) {
            val center = (i + 0.5f) / scale - 0.5f
            val first = floor(center - support).toInt() + 1
            start[i] = first
            var sum = 0f
            for (k in 0 until n) {
                val w = kernel((first + k - center) / stretch)
                weights[i][k] = w
                sum += w
            }
            if (sum != 0f) for (k in 0 until n) weights[i][k] /= sum
        }
        return Taps(start, weights)
    }

    fun resize(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val sw = src.width
        val sh = src.height
        if (dstW <= 0 || dstH <= 0 || (dstW == sw && dstH == sh)) return src
        val input = IntArray(sw * sh)
        src.getPixels(input, 0, sw, 0, 0, sw, sh)

        // pass 1: horizontal, sw -> dstW, keeping sh rows (channels kept as floats per row)
        val tx = taps(sw, dstW)
        val mid = FloatArray(dstW * sh * 3)
        for (y in 0 until sh) {
            val row = y * sw
            for (x in 0 until dstW) {
                val st = tx.start[x]
                val ws = tx.weights[x]
                var r = 0f; var g = 0f; var b = 0f
                for (k in ws.indices) {
                    val w = ws[k]
                    if (w == 0f) continue
                    val p = input[row + min(sw - 1, max(0, st + k))]
                    r += w * ((p shr 16) and 0xFF)
                    g += w * ((p shr 8) and 0xFF)
                    b += w * (p and 0xFF)
                }
                val o = (y * dstW + x) * 3
                mid[o] = r; mid[o + 1] = g; mid[o + 2] = b
            }
        }

        // pass 2: vertical, sh -> dstH
        val ty = taps(sh, dstH)
        val out = IntArray(dstW * dstH)
        for (y in 0 until dstH) {
            val st = ty.start[y]
            val ws = ty.weights[y]
            for (x in 0 until dstW) {
                var r = 0f; var g = 0f; var b = 0f
                for (k in ws.indices) {
                    val w = ws[k]
                    if (w == 0f) continue
                    val o = (min(sh - 1, max(0, st + k)) * dstW + x) * 3
                    r += w * mid[o]; g += w * mid[o + 1]; b += w * mid[o + 2]
                }
                out[y * dstW + x] = (0xFF shl 24) or
                    (r.roundToInt().coerceIn(0, 255) shl 16) or
                    (g.roundToInt().coerceIn(0, 255) shl 8) or
                    b.roundToInt().coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(out, dstW, dstH, Bitmap.Config.ARGB_8888)
    }
}
