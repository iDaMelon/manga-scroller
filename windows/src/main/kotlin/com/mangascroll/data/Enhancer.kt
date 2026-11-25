package com.mangascroll.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.coroutines.coroutineContext

/**
 * Where AI-enhanced pages live and how big they are.
 * Enhanced pages are 1.5x the original size, saved as WebP, next to the originals:
 *   library/<manga>/enhanced/<chapter>/p0000.webp ... and a ".done" marker.
 */
object Enhanced {
    fun dims(w: Int, h: Int): Pair<Int, Int> = (w * 3 + 1) / 2 to (h * 3 + 1) / 2
    fun root(lib: Library, mangaId: String) = File(lib.mangaDir(mangaId), "enhanced")
    fun dir(lib: Library, mangaId: String, chapterId: String) = File(root(lib, mangaId), chapterId)
    fun pageFile(lib: Library, mangaId: String, chapterId: String, index: Int) =
        File(dir(lib, mangaId, chapterId), "p%04d.webp".format(index))
    fun isDone(lib: Library, mangaId: String, chapterId: String) = File(dir(lib, mangaId, chapterId), ".done").exists()
    fun sizeBytes(lib: Library, mangaId: String): Long =
        root(lib, mangaId).walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

data class EnhanceStatus(
    val mangaId: String,
    val chapterId: String,
    val chapterName: String,
    val done: Int,
    val total: Int,
    val queued: Int,
)

/**
 * Runs Real-ESRGAN (realesrgan-ncnn-vulkan, anime model) on the GPU, one chapter at a time.
 * Pages are cut into overlapping strips (very tall pages are too big to upscale in one go),
 * upscaled 4x, stitched back together, scaled to 1.5x and saved as WebP.
 */
class Enhancer(private val library: Library, scope: CoroutineScope) {

    val engine: File? = findEngine()
    val available: Boolean get() = engine != null

    private data class Job(val mangaId: String, val chapterId: String)

    private val lock = Any()
    private val queue = ArrayDeque<Job>()
    private var current: Job? = null
    @Volatile private var cancelCurrent = false
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val _status = MutableStateFlow<EnhanceStatus?>(null)
    val status: StateFlow<EnhanceStatus?> = _status.asStateFlow()

    /** Bumped whenever a chapter finishes or enhanced pages are deleted. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors: Channel<String> get() = _errors

    private val tmpRoot = File(library.root, "_ai_tmp")

    init {
        tmpRoot.deleteRecursively()
        if (engine != null) scope.launch(Dispatchers.IO) { workLoop() }
    }

    fun isQueuedOrRunning(mangaId: String, chapterId: String) = synchronized(lock) {
        current == Job(mangaId, chapterId) || Job(mangaId, chapterId) in queue
    }

    /** Adds chapters to the queue (skipping ones already done). [front] = do these first. */
    fun request(mangaId: String, chapterIds: List<String>, front: Boolean = false) {
        if (engine == null) return
        synchronized(lock) {
            val jobs = chapterIds
                .filter { !Enhanced.isDone(library, mangaId, it) }
                .map { Job(mangaId, it) }
                .filter { it != current }
            queue.removeAll(jobs.toSet())
            if (front) jobs.asReversed().forEach { queue.addFirst(it) } else queue.addAll(jobs)
        }
        wake.trySend(Unit)
    }

    /** Drops queued work for a manga (or just some chapters) and stops the running job if it matches. */
    fun cancel(mangaId: String, chapterIds: Set<String>? = null) = synchronized(lock) {
        queue.removeAll { it.mangaId == mangaId && (chapterIds == null || it.chapterId in chapterIds) }
        current?.let { if (it.mangaId == mangaId && (chapterIds == null || it.chapterId in chapterIds)) cancelCurrent = true }
    }

    fun delete(mangaId: String, chapterIds: Collection<String>? = null) {
        cancel(mangaId, chapterIds?.toSet())
        if (chapterIds == null) Enhanced.root(library, mangaId).deleteRecursively()
        else chapterIds.forEach { Enhanced.dir(library, mangaId, it).deleteRecursively() }
        _version.value++
    }

    private suspend fun workLoop() {
        while (coroutineContext.isActive) {
            val job = synchronized(lock) {
                queue.removeFirstOrNull().also { current = it; cancelCurrent = false }
            }
            if (job == null) {
                _status.value = null
                wake.receive()
                continue
            }
            try {
                enhanceChapter(job)
            } catch (e: CancelledJob) {
                Enhanced.dir(library, job.mangaId, job.chapterId).deleteRecursively()
            } catch (e: Throwable) {
                Enhanced.dir(library, job.mangaId, job.chapterId).deleteRecursively()
                val name = library.find(job.mangaId)?.chapters?.firstOrNull { it.id == job.chapterId }?.name ?: "a chapter"
                _errors.trySend("AI enhance failed on $name: ${e.message ?: e.javaClass.simpleName}")
                // a broken engine would fail every job; stop instead of spamming
                if (e is EngineError) synchronized(lock) { queue.clear() }
            } finally {
                File(tmpRoot, job.chapterId).deleteRecursively()
                synchronized(lock) { current = null }
            }
        }
    }

    private class CancelledJob : Exception()
    private class EngineError(msg: String) : Exception(msg)

    private fun checkCancel() {
        if (cancelCurrent) throw CancelledJob()
    }

    private fun enhanceChapter(job: Job) {
        val manga = library.find(job.mangaId) ?: return
        val chapter = manga.chapters.firstOrNull { it.id == job.chapterId } ?: return
        if (Enhanced.isDone(library, job.mangaId, job.chapterId)) return
        val exe = engine ?: return
        Images.ensureLoaded()

        val work = File(tmpRoot, job.chapterId)
        val inDir = File(work, "in").apply { mkdirs() }
        val outDir = File(work, "out").apply { mkdirs() }
        fun report(done: Int, total: Int) {
            val queued = synchronized(lock) { queue.size }
            _status.value = EnhanceStatus(job.mangaId, job.chapterId, chapter.name, done, total, queued)
        }

        // 1. cut every page into overlapping strips
        val plans = chapter.pages.mapIndexed { index, page ->
            checkCancel()
            val src = library.pageFile(job.mangaId, job.chapterId, page)
            val img = ImageIO.read(src) ?: throw IllegalStateException("can't read ${src.name}")
            val rgb = Images.toRgb(img)
            val strips = strips(rgb.height)
            strips.forEachIndexed { k, s ->
                val crop = rgb.getSubimage(0, s.c0, rgb.width, s.c1 - s.c0)
                ImageIO.write(crop, "png", File(inDir, stripName(index, k)))
            }
            PagePlan(index, rgb.width, rgb.height, strips)
        }
        val totalStrips = plans.sumOf { it.strips.size }
        report(0, totalStrips)

        // 2. upscale all strips in one engine run (the model loads once per chapter)
        val modelDir = File(exe.parentFile, "models")
        val log = File(work, "engine.log")
        val process = ProcessBuilder(
            exe.path, "-i", inDir.path, "-o", outDir.path,
            "-n", MODEL, "-s", "4", "-f", "png", "-m", modelDir.path,
        ).directory(exe.parentFile).redirectErrorStream(true).redirectOutput(log).start()
        try {
            while (!process.waitFor(400, TimeUnit.MILLISECONDS)) {
                if (cancelCurrent) {
                    process.destroyForcibly(); throw CancelledJob()
                }
                report(outDir.list()?.size ?: 0, totalStrips)
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        val produced = outDir.list()?.size ?: 0
        if (process.exitValue() != 0 || produced < totalStrips) {
            val tail = runCatching { log.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.endsWith("%") }.takeLast(2) }
                .getOrDefault(emptyList()).joinToString(" / ")
            throw EngineError(if (tail.isNotBlank()) tail else "the AI engine stopped unexpectedly (is your graphics driver up to date?)")
        }
        report(totalStrips, totalStrips)

        // 3. stitch each page back together at 1.5x and save as WebP
        val dest = Enhanced.dir(library, job.mangaId, job.chapterId).apply { mkdirs() }
        plans.forEach { plan ->
            checkCancel()
            val bytes = stitch(plan, outDir)
            File(dest, "p%04d.webp".format(plan.index)).writeBytes(bytes)
        }
        File(dest, ".done").writeText(MODEL)
        _version.value++
    }

    private data class Strip(val s0: Int, val s1: Int, val c0: Int, val c1: Int)
    private data class PagePlan(val index: Int, val w: Int, val h: Int, val strips: List<Strip>)

    private fun stripName(page: Int, strip: Int) = "p%04d_%03d.png".format(page, strip)

    /** Rows [s0,s1) are kept from each strip; [c0,c1) adds overlap so the edges blend. */
    private fun strips(h: Int): List<Strip> {
        val out = mutableListOf<Strip>()
        var s0 = 0
        while (s0 < h) {
            val s1 = minOf(h, s0 + STRIP)
            out += Strip(s0, s1, maxOf(0, s0 - OVERLAP), minOf(h, s1 + OVERLAP))
            s0 = s1
        }
        return out
    }

    private fun stitch(plan: PagePlan, outDir: File): ByteArray {
        val (w2, h2) = Enhanced.dims(plan.w, plan.h)
        val page = Surface.makeRasterN32Premul(w2, h2)
        page.canvas.clear(Color.WHITE)
        plan.strips.forEachIndexed { k, s ->
            val up = Image.makeFromEncoded(File(outDir, stripName(plan.index, k)).readBytes())
            val stripH = s.c1 - s.c0
            if (up.width != plan.w * 4 || up.height != stripH * 4) {
                throw EngineError("unexpected output size ${up.width}x${up.height}")
            }
            // 4x -> 2x: an exact half, so bilinear averages each 2x2 block cleanly
            val half = Surface.makeRasterN32Premul(plan.w * 2, stripH * 2)
            half.canvas.drawImageRect(
                up, Rect.makeWH(up.width.toFloat(), up.height.toFloat()),
                Rect.makeWH((plan.w * 2).toFloat(), (stripH * 2).toFloat()),
                FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), null, true,
            )
            val halfImg = half.makeImageSnapshot()
            // 2x -> 1.5x with a cubic filter; only the strip's own rows are drawn
            val top = s.s0 * 1.5f
            val bottom = if (s.s1 == plan.h) h2.toFloat() else s.s1 * 1.5f
            page.canvas.drawImageRect(
                halfImg,
                Rect.makeLTRB(0f, ((s.s0 - s.c0) * 2).toFloat(), (plan.w * 2).toFloat(), ((s.s1 - s.c0) * 2).toFloat()),
                Rect.makeLTRB(0f, top, w2.toFloat(), bottom),
                SamplingMode.MITCHELL, null, false,
            )
            halfImg.close(); half.close(); up.close()
        }
        val data = page.makeImageSnapshot().encodeToData(EncodedImageFormat.WEBP, QUALITY)
            ?: throw IllegalStateException("couldn't save WebP")
        page.close()
        return data.bytes
    }

    companion object {
        const val MODEL = "realesrgan-x4plus-anime"
        private const val STRIP = 1024 // source rows per strip (even)
        private const val OVERLAP = 16 // extra rows on each side (even, so 1.5x lands on whole pixels)
        private const val QUALITY = 90

        /** ai\realesrgan-ncnn-vulkan.exe next to the app folder, or MANGASCROLL_AI. */
        fun findEngine(): File? {
            val win = System.getProperty("os.name").lowercase().contains("win")
            val name = if (win) "realesrgan-ncnn-vulkan.exe" else "realesrgan-ncnn-vulkan"
            val jarDir = runCatching {
                File(Enhancer::class.java.protectionDomain.codeSource.location.toURI()).parentFile
            }.getOrNull()
            val candidates = listOfNotNull(
                System.getenv("MANGASCROLL_AI")?.takeIf { it.isNotBlank() }?.let { File(it) },
                jarDir?.parentFile?.let { File(it, "ai") },
                File("ai"),
            )
            return candidates.map { File(it, name) }.firstOrNull { it.isFile }
        }
    }
}
