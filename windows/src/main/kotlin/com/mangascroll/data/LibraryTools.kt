package com.mangascroll.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Swaps original pages for their AI-enhanced versions. The originals are deleted. */
object Replacer {
    /** Returns the bytes freed (the deleted originals), or null if the chapter isn't fully enhanced. */
    suspend fun replace(lib: Library, mangaId: String, chapterId: String): Long? = withContext(Dispatchers.IO) {
        val chapter = lib.find(mangaId)?.chapters?.firstOrNull { it.id == chapterId } ?: return@withContext null
        if (chapter.aiReplaced || !Enhanced.isDone(lib, mangaId, chapterId)) return@withContext null
        val dir = lib.chapterDir(mangaId, chapterId)
        val sources = chapter.pages.indices.map { Enhanced.pageFile(lib, mangaId, chapterId, it) }
        if (sources.any { !it.isFile }) return@withContext null

        val before = chapter.pages.sumOf { File(dir, it.file).length() }
        val newPages = chapter.pages.mapIndexed { i, p ->
            val name = "ai%04d.webp".format(i)
            val target = File(dir, name)
            Files.move(sources[i].toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            val (w, h) = Images.size(target) ?: Enhanced.dims(p.w, p.h)
            Page(name, w, h)
        }
        // record the swap first, then remove the old files
        lib.updateChapter(mangaId, chapterId) { it.copy(pages = newPages, aiReplaced = true) }
        val keep = newPages.map { it.file }.toSet()
        chapter.pages.filter { it.file !in keep }.forEach { File(dir, it.file).delete() }
        Enhanced.dir(lib, mangaId, chapterId).deleteRecursively()
        before
    }
}

/** Moves or switches the folder the library lives in. */
object LibraryMover {
    private val prefs = java.util.prefs.Preferences.userRoot().node("mangascroll")

    /** The folder chosen in Settings, if any. */
    fun savedDir(): File? = prefs.get("libraryDir", null)?.let { File(it) }

    fun remember(dir: File) = prefs.put("libraryDir", dir.absolutePath)

    fun hasLibrary(dir: File) = File(dir, "library.json").isFile

    fun countManga(dir: File): Int = runCatching {
        org.json.JSONObject(File(dir, "library.json").readText()).optJSONArray("manga")?.length() ?: 0
    }.getOrDefault(0)

    fun sizeOf(dir: File): Long = File(dir, "library").walkTopDown().filter { it.isFile }.sumOf { it.length() } +
        File(dir, "library.json").length()

    /**
     * Moves library.json and library/ from [from] into [to]. A quick rename when both are on the
     * same drive, otherwise copy everything (reporting progress) and then delete the old copy.
     */
    fun move(from: File, to: File, onProgress: (done: Int, total: Int) -> Unit) {
        to.mkdirs()
        val names = listOf("library", "library.json")
        try {
            names.forEach { n ->
                val src = File(from, n)
                if (src.exists()) Files.move(src.toPath(), File(to, n).toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            return
        } catch (e: Exception) {
            // different drive: fall through to copy (undo any partial rename first)
            names.forEach { n ->
                val moved = File(to, n)
                if (moved.exists() && !File(from, n).exists()) Files.move(moved.toPath(), File(from, n).toPath())
            }
        }
        val files = names.flatMap { n -> File(from, n).walkTopDown().filter { it.isFile }.toList() }
        files.forEachIndexed { i, f ->
            val rel = f.relativeTo(from)
            val dest = File(to, rel.path)
            dest.parentFile.mkdirs()
            Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            if (dest.length() != f.length()) throw IllegalStateException("copy of ${rel.path} is incomplete")
            onProgress(i + 1, files.size)
        }
        names.forEach { File(from, it).deleteRecursively() }
    }

    /** Starts MangaScroll again (through MangaScroll.exe when available), then exits this copy. */
    fun relaunch(): Boolean {
        val jar = runCatching { File(LibraryMover::class.java.protectionDomain.codeSource.location.toURI()) }.getOrNull()
        val exe = jar?.parentFile?.parentFile?.let { File(it, "MangaScroll.exe") }
        val cmd = when {
            exe != null && exe.isFile -> listOf(exe.path)
            jar != null && jar.isFile -> {
                val bin = File(System.getProperty("java.home"), "bin")
                val java = listOf("javaw.exe", "java").map { File(bin, it) }.first { it.isFile || it.name == "java" }
                listOf(java.path, "-jar", jar.path)
            }
            else -> return false
        }
        ProcessBuilder(cmd).start()
        return true
    }
}
