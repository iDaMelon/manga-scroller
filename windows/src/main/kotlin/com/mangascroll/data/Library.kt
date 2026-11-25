package com.mangascroll.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stores every imported manga in the app's data folder, indexed by library.json. */
class Library(val baseDir: File) {

    val root = File(baseDir, "library").apply { mkdirs() }
    private val index = File(baseDir, "library.json")
    private val mutex = Mutex()
    private var loaded = false

    private val _items = MutableStateFlow<List<Manga>>(emptyList())
    val items: StateFlow<List<Manga>> = _items.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun mangaDir(mangaId: String) = File(root, mangaId)
    fun chapterDir(mangaId: String, chapterId: String) = File(mangaDir(mangaId), chapterId)
    fun pageFile(mangaId: String, chapterId: String, page: Page) = File(chapterDir(mangaId, chapterId), page.file)
    fun coverFile(mangaId: String) = File(mangaDir(mangaId), "cover.jpg")

    fun find(mangaId: String): Manga? = _items.value.firstOrNull { it.id == mangaId }

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        val list = withContext(Dispatchers.IO) {
            runCatching { if (index.exists()) parse(index.readText()) else emptyList() }.getOrElse { emptyList() }
        }
        _items.value = list
        loaded = true
        _ready.value = true
    }

    suspend fun update(transform: (List<Manga>) -> List<Manga>) = mutex.withLock {
        val next = transform(_items.value)
        _items.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(index.parentFile, "library.json.tmp")
            tmp.writeText(serialize(next))
            if (!tmp.renameTo(index)) {
                index.delete(); tmp.renameTo(index)
            }
        }
    }

    suspend fun updateManga(mangaId: String, transform: (Manga) -> Manga) =
        update { list -> list.map { if (it.id == mangaId) transform(it) else it } }

    suspend fun updateChapter(mangaId: String, chapterId: String, transform: (Chapter) -> Chapter) =
        updateManga(mangaId) { m -> m.copy(chapters = m.chapters.map { if (it.id == chapterId) transform(it) else it }) }

    suspend fun deleteManga(mangaId: String) {
        update { list -> list.filterNot { it.id == mangaId } }
        withContext(Dispatchers.IO) { mangaDir(mangaId).deleteRecursively() }
    }

    suspend fun deleteChapter(mangaId: String, chapterId: String) {
        updateManga(mangaId) { m -> m.copy(chapters = m.chapters.filterNot { it.id == chapterId }) }
        withContext(Dispatchers.IO) { chapterDir(mangaId, chapterId).deleteRecursively() }
    }

    // ---------- JSON ----------

    private fun serialize(list: List<Manga>): String {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id); put("title", m.title); put("created", m.created)
                put("lastRead", m.lastRead); put("lastChapter", m.lastChapterId ?: JSONObject.NULL)
                put("enhance", m.enhance)
                put("chapters", JSONArray().apply {
                    m.chapters.forEach { c ->
                        put(JSONObject().apply {
                            put("id", c.id); put("name", c.name); put("added", c.added)
                            put("posIndex", c.posIndex); put("posOffset", c.posOffset)
                            put("progress", c.progress.toDouble()); put("read", c.read); put("aiReplaced", c.aiReplaced)
                            put("pages", JSONArray().apply {
                                c.pages.forEach { p -> put(JSONObject().put("f", p.file).put("w", p.w).put("h", p.h)) }
                            })
                        })
                    }
                })
            })
        }
        return JSONObject().put("version", 1).put("manga", arr).toString()
    }

    private fun parse(text: String): List<Manga> {
        val arr = JSONObject(text).optJSONArray("manga") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val chArr = o.optJSONArray("chapters") ?: JSONArray()
            Manga(
                id = o.getString("id"),
                title = o.optString("title", "Untitled"),
                created = o.optLong("created"),
                lastRead = o.optLong("lastRead"),
                lastChapterId = if (o.isNull("lastChapter")) null else o.optString("lastChapter"),
                enhance = o.optString("enhance", "off"),
                chapters = (0 until chArr.length()).map { j ->
                    val c = chArr.getJSONObject(j)
                    val pArr = c.optJSONArray("pages") ?: JSONArray()
                    Chapter(
                        id = c.getString("id"),
                        name = c.optString("name", "Chapter"),
                        added = c.optLong("added"),
                        posIndex = c.optInt("posIndex"),
                        posOffset = c.optInt("posOffset"),
                        progress = c.optDouble("progress", 0.0).toFloat(),
                        read = c.optBoolean("read"),
                        aiReplaced = c.optBoolean("aiReplaced"),
                        pages = (0 until pArr.length()).map { k ->
                            val p = pArr.getJSONObject(k)
                            Page(p.getString("f"), p.getInt("w"), p.getInt("h"))
                        },
                    )
                },
            )
        }
    }

    companion object {
        /** %APPDATA%\MangaScroll on Windows, ~/.mangascroll elsewhere. */
        fun defaultDir(): File {
            val appData = System.getenv("APPDATA")
            val dir = if (!appData.isNullOrBlank()) File(appData, "MangaScroll") else File(System.getProperty("user.home"), ".mangascroll")
            return dir.apply { mkdirs() }
        }
    }
}
