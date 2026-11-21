package com.mangascroll.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.coroutines.coroutineContext

sealed interface ImportSource {
    data class Images(val files: List<File>) : ImportSource
    data class Folder(val dir: File) : ImportSource
    data class Zips(val files: List<File>) : ImportSource
}

data class ImportedChapter(val chapter: Chapter, val sourceName: String)

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
private val zipExt = setOf("zip", "cbz")

fun isImageName(name: String) = !name.startsWith(".") && name.substringAfterLast('.', "").lowercase() in imageExt
fun isZipName(name: String) = name.substringAfterLast('.', "").lowercase() in zipExt

/** Works out what kind of import a set of dropped / picked files is. */
fun sourceFor(files: List<File>): ImportSource? {
    val dirs = files.filter { it.isDirectory }
    val zips = files.filter { it.isFile && isZipName(it.name) }
    val imgs = files.filter { it.isFile && isImageName(it.name) }
    return when {
        dirs.size == 1 && zips.isEmpty() && imgs.isEmpty() -> ImportSource.Folder(dirs[0])
        dirs.isEmpty() && zips.isNotEmpty() && imgs.isEmpty() -> ImportSource.Zips(zips)
        dirs.isEmpty() && zips.isEmpty() && imgs.isNotEmpty() -> ImportSource.Images(imgs)
        else -> null
    }
}

class Importer(private val library: Library) {

    fun suggestTitle(source: ImportSource): String = when (source) {
        is ImportSource.Images -> source.files.firstOrNull()?.parentFile?.name ?: ""
        is ImportSource.Folder -> source.dir.name
        is ImportSource.Zips -> source.files.firstOrNull()?.nameWithoutExtension ?: ""
    }

    suspend fun import(
        source: ImportSource,
        mangaId: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<ImportedChapter> = withContext(Dispatchers.IO) {
        Images.ensureLoaded()
        val result = mutableListOf<ImportedChapter>()
        when (source) {
            is ImportSource.Images -> {
                var done = 0
                buildChapter(mangaId, "Chapter", source.files.map { f -> f.name to { f.inputStream() } }) {
                    onProgress(++done, source.files.size)
                }?.let { result += it }
            }

            is ImportSource.Folder -> {
                val children = source.dir.listFiles()?.toList().orEmpty()
                val groups = mutableListOf<Pair<String, List<File>>>()
                val rootImages = children.filter { it.isFile && isImageName(it.name) }
                if (rootImages.isNotEmpty()) groups += source.dir.name to rootImages
                children.filter { it.isDirectory }
                    .sortedWith(compareBy(NaturalOrder) { it.name })
                    .forEach { dir ->
                        val imgs = dir.listFiles()?.filter { it.isFile && isImageName(it.name) }.orEmpty()
                        if (imgs.isNotEmpty()) groups += dir.name to imgs
                    }
                val total = groups.sumOf { it.second.size }
                var done = 0
                groups.forEach { (name, files) ->
                    buildChapter(mangaId, name, files.map { f -> f.name to { f.inputStream() } }) {
                        onProgress(++done, total)
                    }?.let { result += it }
                }
            }

            is ImportSource.Zips -> {
                var done = 0
                source.files.sortedWith(compareBy(NaturalOrder) { it.name }).forEach { zip ->
                    result += importZip(mangaId, zip) { onProgress(++done, -1) }
                }
            }
        }
        result
    }

    private suspend fun buildChapter(
        mangaId: String,
        name: String,
        items: List<Pair<String, () -> InputStream>>,
        tick: () -> Unit,
    ): ImportedChapter? {
        val chapterId = newId()
        val dir = library.chapterDir(mangaId, chapterId).apply { mkdirs() }
        val saved = mutableListOf<Pair<String, Page>>()
        items.forEachIndexed { i, (origName, open) ->
            coroutineContext.ensureActive()
            val out = File(dir, "p%04d.%s".format(i, origName.substringAfterLast('.', "img").lowercase().take(5)))
            open().use { input -> out.outputStream().use { input.copyTo(it) } }
            measure(out)?.let { saved += origName to it } ?: out.delete()
            tick()
        }
        return finishChapter(dir, chapterId, name, saved)
    }

    private suspend fun importZip(mangaId: String, file: File, tick: () -> Unit): List<ImportedChapter> {
        val groups = linkedMapOf<String, Pair<String, MutableList<Pair<String, Page>>>>()
        var counter = 0
        ZipFile(file).use { zip ->
            for (entry in zip.entries()) {
                coroutineContext.ensureActive()
                val path = entry.name.replace('\\', '/')
                val base = path.substringAfterLast('/')
                if (entry.isDirectory || path.contains("__MACOSX") || !isImageName(base)) continue
                val folder = path.substringBeforeLast('/', "")
                val group = groups.getOrPut(folder) { newId() to mutableListOf() }
                val dir = library.chapterDir(mangaId, group.first).apply { mkdirs() }
                val out = File(dir, "p%05d.%s".format(counter++, base.substringAfterLast('.').lowercase()))
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                measure(out)?.let { group.second += base to it } ?: out.delete()
                tick()
            }
        }
        return groups.entries
            .sortedWith(compareBy(NaturalOrder) { it.key })
            .mapNotNull { (folder, g) ->
                val name = if (groups.size == 1 || folder.isEmpty()) file.nameWithoutExtension else folder.substringAfterLast('/')
                finishChapter(library.chapterDir(mangaId, g.first), g.first, name, g.second)
            }
    }

    private fun finishChapter(dir: File, chapterId: String, name: String, saved: List<Pair<String, Page>>): ImportedChapter? {
        if (saved.isEmpty()) {
            dir.deleteRecursively(); return null
        }
        val pages = saved.sortedWith(compareBy(NaturalOrder) { it.first }).map { it.second }
        return ImportedChapter(Chapter(chapterId, name, System.currentTimeMillis(), pages), name)
    }

    private fun measure(file: File): Page? = Images.size(file)?.let { (w, h) -> Page(file.name, w, h) }

    /** Crops the top of the first page into a 3:4 cover thumbnail. */
    suspend fun makeCover(mangaId: String) = withContext(Dispatchers.IO) {
        val manga = library.find(mangaId) ?: return@withContext
        val chapter = manga.chapters.firstOrNull() ?: return@withContext
        val page = chapter.pages.firstOrNull() ?: return@withContext
        val src = library.pageFile(mangaId, chapter.id, page)
        val cropH = minOf(page.h, page.w * 4 / 3)
        var sample = 1
        while (page.w / (sample * 2) >= 360) sample *= 2
        val region = Images.readRegion(src, page.w, 0, cropH, sample) ?: return@withContext
        // smooth final resize to 360 wide
        val tw = minOf(360, region.width)
        val th = (region.height * tw.toFloat() / region.width).toInt().coerceAtLeast(1)
        val out = BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, tw, th)
        g.drawImage(region, 0, 0, tw, th, null)
        g.dispose()
        ImageIO.write(out, "jpg", library.coverFile(mangaId))
    }
}
