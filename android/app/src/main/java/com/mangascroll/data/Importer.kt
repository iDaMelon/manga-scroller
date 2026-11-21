package com.mangascroll.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

sealed interface ImportSource {
    data class Images(val uris: List<Uri>) : ImportSource
    data class Folder(val uri: Uri) : ImportSource
    data class Zips(val uris: List<Uri>) : ImportSource
}

/** A chapter as it came out of the import; [sourceName] is the folder / zip it came from. */
data class ImportedChapter(val chapter: Chapter, val sourceName: String)

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

class Importer(private val context: Context, private val library: Library) {

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
    private val zipExt = setOf("zip", "cbz")

    private fun isImage(name: String) =
        !name.startsWith(".") && name.substringAfterLast('.', "").lowercase() in imageExt

    fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: ""
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: ""
    }

    fun suggestTitle(source: ImportSource): String = when (source) {
        is ImportSource.Images -> ""
        is ImportSource.Folder -> DocumentFile.fromTreeUri(context, source.uri)?.name ?: ""
        is ImportSource.Zips -> source.uris.firstOrNull()?.let { displayName(it).substringBeforeLast('.') } ?: ""
    }

    /**
     * Copies the images into the manga's folder. Each group (picked images, a sub-folder,
     * a folder inside a zip, or a zip file) becomes one chapter.
     */
    suspend fun import(
        source: ImportSource,
        mangaId: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<ImportedChapter> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ImportedChapter>()
        when (source) {
            is ImportSource.Images -> {
                // the picker only offers images; anything that isn't decodable is skipped later
                val items = source.uris.map { displayName(it) to it }
                var done = 0
                buildChapter(mangaId, "Chapter", items.map { (name, uri) ->
                    name to { context.contentResolver.openInputStream(uri) }
                }) { onProgress(++done, items.size) }?.let { result += it }
            }

            is ImportSource.Folder -> {
                val root = DocumentFile.fromTreeUri(context, source.uri)
                    ?: throw IllegalStateException("Couldn't open that folder")
                val rootName = root.name ?: "Folder"
                val children = root.listFiles()
                val groups = mutableListOf<Pair<String, List<DocumentFile>>>()
                val rootImages = children.filter { it.isFile && isImage(it.name ?: "") }
                if (rootImages.isNotEmpty()) groups += rootName to rootImages
                children.filter { it.isDirectory }
                    .sortedWith(compareBy(NaturalOrder) { it.name ?: "" })
                    .forEach { dir ->
                        val imgs = dir.listFiles().filter { it.isFile && isImage(it.name ?: "") }
                        if (imgs.isNotEmpty()) groups += (dir.name ?: "Chapter") to imgs
                    }
                val total = groups.sumOf { it.second.size }
                var done = 0
                groups.forEach { (name, files) ->
                    buildChapter(mangaId, name, files.map { f ->
                        (f.name ?: "") to { context.contentResolver.openInputStream(f.uri) }
                    }) { onProgress(++done, total) }?.let { result += it }
                }
            }

            is ImportSource.Zips -> {
                var done = 0
                source.uris.sortedWith(compareBy(NaturalOrder) { displayName(it) }).forEach { uri ->
                    val zipName = displayName(uri).substringBeforeLast('.')
                    result += importZip(mangaId, uri, zipName) { onProgress(++done, -1) }
                }
            }
        }
        result
    }

    private suspend fun buildChapter(
        mangaId: String,
        name: String,
        items: List<Pair<String, () -> InputStream?>>,
        tick: () -> Unit,
    ): ImportedChapter? {
        val chapterId = newId()
        val dir = library.chapterDir(mangaId, chapterId).apply { mkdirs() }
        val saved = mutableListOf<Pair<String, Page>>()
        items.forEachIndexed { i, (origName, open) ->
            coroutineContext.ensureActive()
            val ext = origName.substringAfterLast('.', "img").lowercase().take(5)
            val out = File(dir, "p%04d.%s".format(i, ext))
            open()?.use { input -> out.outputStream().use { input.copyTo(it) } }
            measure(out)?.let { saved += origName to it } ?: out.delete()
            tick()
        }
        return finishChapter(dir, chapterId, name, saved)
    }

    private suspend fun importZip(mangaId: String, uri: Uri, zipName: String, tick: () -> Unit): List<ImportedChapter> {
        // folder inside zip -> list of (original name, saved page)
        val groups = linkedMapOf<String, Pair<String, MutableList<Pair<String, Page>>>>()
        var counter = 0
        val input = context.contentResolver.openInputStream(uri) ?: return emptyList()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                coroutineContext.ensureActive()
                val path = entry.name.replace('\\', '/')
                val base = path.substringAfterLast('/')
                if (entry.isDirectory || path.contains("__MACOSX") || !isImage(base)) continue
                val folder = path.substringBeforeLast('/', "")
                val group = groups.getOrPut(folder) { newId() to mutableListOf() }
                val dir = library.chapterDir(mangaId, group.first).apply { mkdirs() }
                val out = File(dir, "p%05d.%s".format(counter++, base.substringAfterLast('.').lowercase()))
                out.outputStream().use { zip.copyTo(it) }
                measure(out)?.let { group.second += base to it } ?: out.delete()
                tick()
            }
        }
        return groups.entries
            .sortedWith(compareBy(NaturalOrder) { it.key })
            .mapNotNull { (folder, g) ->
                val name = if (groups.size == 1 || folder.isEmpty()) zipName else folder.substringAfterLast('/')
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

    private fun measure(file: File): Page? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        return if (o.outWidth > 0 && o.outHeight > 0) Page(file.name, o.outWidth, o.outHeight) else null
    }

    /** Crops the top of the first page into a 3:4 cover thumbnail. */
    suspend fun makeCover(mangaId: String) = withContext(Dispatchers.IO) {
        val manga = library.find(mangaId) ?: return@withContext
        val chapter = manga.chapters.firstOrNull() ?: return@withContext
        val page = chapter.pages.firstOrNull() ?: return@withContext
        val src = library.pageFile(mangaId, chapter.id, page)
        val cropH = minOf(page.h, page.w * 4 / 3)
        var sample = 1
        while (page.w / (sample * 2) >= 360) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp: Bitmap? = runCatching {
            @Suppress("DEPRECATION")
            val dec = BitmapRegionDecoder.newInstance(src.path, false)
            try { dec?.decodeRegion(Rect(0, 0, page.w, cropH), opts) } finally { dec?.recycle() }
        }.getOrNull() ?: runCatching {
            BitmapFactory.decodeFile(src.path, opts)?.let { full ->
                val h = minOf(full.height, full.width * 4 / 3)
                Bitmap.createBitmap(full, 0, 0, full.width, h)
            }
        }.getOrNull()
        bmp?.let { b ->
            library.coverFile(mangaId).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
    }
}
