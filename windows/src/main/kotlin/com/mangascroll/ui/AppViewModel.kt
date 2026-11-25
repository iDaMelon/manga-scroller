package com.mangascroll.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mangascroll.data.Enhancer
import com.mangascroll.data.ImportSource
import com.mangascroll.data.Importer
import com.mangascroll.data.Library
import com.mangascroll.data.Manga
import com.mangascroll.data.NaturalOrder
import com.mangascroll.data.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data class Detail(val mangaId: String) : Screen
    data class Reader(val mangaId: String, val chapterId: String) : Screen
}

data class PendingImport(
    val source: ImportSource,
    val targetMangaId: String?,
    val title: String,
    val chapterName: String,
)

sealed interface ImportStatus {
    data class Running(val done: Int, val total: Int) : ImportStatus
    data class Failed(val message: String) : ImportStatus
}

class AppViewModel(val library: Library) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val importer = Importer(library)
    private val saves = mutableListOf<Job>()
    private val prefs = java.util.prefs.Preferences.userRoot().node("mangascroll")

    /** Runs the AI upscaler in the background. */
    val enhancer = Enhancer(library, viewModelScope)

    /** Reader shows AI-enhanced pages when they exist (toggle in the reader). */
    var showEnhanced by mutableStateOf(prefs.getBoolean("showEnhanced", true))
        private set

    fun toggleEnhanced() {
        showEnhanced = !showEnhanced
        prefs.putBoolean("showEnhanced", showEnhanced)
    }

    val manga = library.items
    val ready = library.ready

    val stack = mutableStateListOf<Screen>(Screen.Home)

    /** Reader full-screen toggle (F11). */
    var fullscreen by mutableStateOf(false)

    /** Message shown briefly at the bottom of the window. */
    var toast by mutableStateOf<String?>(null)

    var pending by mutableStateOf<PendingImport?>(null)
        private set

    private val _status = MutableStateFlow<ImportStatus?>(null)
    val status = _status.asStateFlow()
    private var importJob: Job? = null

    /** Bumped when a cover image changes so the home grid reloads it. */
    var coverVersion by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch {
            library.load()
            // resume background work for manga set to enhance everything
            library.items.value.filter { it.enhance == "all" }.forEach { m ->
                enhancer.request(m.id, m.chapters.map { it.id })
            }
        }
        viewModelScope.launch { for (msg in enhancer.errors) toast = msg }
    }

    // ---------- AI enhance ----------

    /** "off", "reading" (current chapter + the next 2) or "all". */
    fun setEnhanceMode(mangaId: String, mode: String) = viewModelScope.launch {
        library.updateManga(mangaId) { it.copy(enhance = mode) }
        val m = library.find(mangaId) ?: return@launch
        when (mode) {
            "off" -> enhancer.cancel(mangaId)
            "all" -> {
                val start = m.continueChapter()?.let { c -> m.chapters.indexOf(c) } ?: 0
                val ids = m.chapters.map { it.id }
                enhancer.request(mangaId, ids.drop(start) + ids.take(start))
            }
            "reading" -> {
                enhancer.cancel(mangaId)
                m.continueChapter()?.let { enhanceAround(m, it.id) }
            }
        }
    }

    /** Called when a chapter opens in the reader. */
    fun onChapterOpened(mangaId: String, chapterId: String) {
        val m = library.find(mangaId) ?: return
        when (m.enhance) {
            "reading" -> enhanceAround(m, chapterId)
            "all" -> {
                val i = m.chapters.indexOfFirst { it.id == chapterId }
                enhancer.request(mangaId, m.chapters.drop(i).take(3).map { it.id }, front = true)
            }
        }
    }

    /** Enhance this chapter and the next 2; drop enhanced chapters more than 2 behind. */
    private fun enhanceAround(m: Manga, chapterId: String) {
        val i = m.chapters.indexOfFirst { it.id == chapterId }.coerceAtLeast(0)
        enhancer.request(m.id, m.chapters.drop(i).take(3).map { it.id }, front = true)
        val old = m.chapters.take((i - 2).coerceAtLeast(0)).map { it.id }
            .filter { com.mangascroll.data.Enhanced.isDone(library, m.id, it) }
        if (old.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { enhancer.delete(m.id, old) }
    }

    fun deleteEnhanced(mangaId: String) = viewModelScope.launch {
        library.updateManga(mangaId) { it.copy(enhance = "off") }
        withContext(Dispatchers.IO) { enhancer.delete(mangaId) }
    }

    fun open(screen: Screen) { stack.add(screen) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceTop(screen: Screen) { stack[stack.lastIndex] = screen }

    // ---------- import ----------

    fun beginImport(source: ImportSource, targetMangaId: String?) {
        val target = targetMangaId?.let { library.find(it) }
        pending = PendingImport(
            source = source,
            targetMangaId = targetMangaId,
            title = importer.suggestTitle(source).ifBlank { "" },
            chapterName = "Chapter ${(target?.chapters?.size ?: 0) + 1}",
        )
    }

    fun cancelPending() { pending = null }

    fun confirmImport(title: String, chapterName: String) {
        val p = pending ?: return
        pending = null
        val isNew = p.targetMangaId == null
        val mangaId = p.targetMangaId ?: newId()
        importJob = viewModelScope.launch {
            _status.value = ImportStatus.Running(0, 0)
            try {
                val imported = importer.import(p.source, mangaId) { done, total ->
                    _status.value = ImportStatus.Running(done, total)
                }
                if (imported.isEmpty()) throw IllegalStateException("No images were found in what you picked.")
                val chapters = if (imported.size == 1) {
                    val c = imported[0]
                    val name = when {
                        p.source is ImportSource.Images -> chapterName.ifBlank { "Chapter 1" }
                        isNew -> "Chapter 1"
                        else -> c.sourceName
                    }
                    listOf(c.chapter.copy(name = name))
                } else imported.map { it.chapter }

                if (isNew) {
                    library.update { list ->
                        list + Manga(
                            id = mangaId,
                            title = title.ifBlank { "Untitled" },
                            created = System.currentTimeMillis(),
                            chapters = chapters,
                        )
                    }
                    importer.makeCover(mangaId)
                    coverVersion++
                    open(Screen.Detail(mangaId))
                } else {
                    library.updateManga(mangaId) { it.copy(chapters = it.chapters + chapters) }
                }
                _status.value = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(NonCancellable) { cleanup(isNew, mangaId) }
                _status.value = null
            } catch (e: Throwable) {
                cleanup(isNew, mangaId)
                _status.value = ImportStatus.Failed(e.message ?: "Import failed")
            }
        }
    }

    private suspend fun cleanup(isNew: Boolean, mangaId: String) {
        if (isNew && library.find(mangaId) == null) {
            withContext(Dispatchers.IO) { library.mangaDir(mangaId).deleteRecursively() }
        }
    }

    fun cancelImport() { importJob?.cancel() }
    fun dismissStatus() { _status.value = null }

    // ---------- edits ----------

    fun renameManga(id: String, title: String) = viewModelScope.launch {
        library.updateManga(id) { it.copy(title = title.ifBlank { it.title }) }
    }

    fun deleteManga(id: String) = viewModelScope.launch {
        enhancer.cancel(id)
        stack.removeAll { (it is Screen.Detail && it.mangaId == id) || (it is Screen.Reader && it.mangaId == id) }
        library.deleteManga(id)
    }

    fun renameChapter(mangaId: String, chapterId: String, name: String) = viewModelScope.launch {
        library.updateChapter(mangaId, chapterId) { it.copy(name = name.ifBlank { it.name }) }
    }

    fun deleteChapter(mangaId: String, chapterId: String) = viewModelScope.launch {
        val wasFirst = library.find(mangaId)?.chapters?.firstOrNull()?.id == chapterId
        withContext(Dispatchers.IO) { enhancer.delete(mangaId, listOf(chapterId)) }
        library.deleteChapter(mangaId, chapterId)
        if (wasFirst) { importer.makeCover(mangaId); coverVersion++ }
    }

    fun setRead(mangaId: String, chapterId: String, read: Boolean) = viewModelScope.launch {
        library.updateChapter(mangaId, chapterId) {
            if (read) it.copy(read = true, progress = 1f) else it.copy(read = false, progress = 0f, posIndex = 0, posOffset = 0)
        }
    }

    fun sortChapters(mangaId: String) = viewModelScope.launch {
        library.updateManga(mangaId) { m -> m.copy(chapters = m.chapters.sortedWith(compareBy(NaturalOrder) { it.name })) }
        importer.makeCover(mangaId); coverVersion++
    }

    fun reverseChapters(mangaId: String) = viewModelScope.launch {
        library.updateManga(mangaId) { m -> m.copy(chapters = m.chapters.reversed()) }
        importer.makeCover(mangaId); coverVersion++
    }

    /** Waits (briefly) for pending saves; called when the window closes. */
    fun flush() = runBlocking {
        val pending = synchronized(saves) { saves.toList() }
        withTimeoutOrNull(3000) { pending.joinAll() }
    }

    fun saveProgress(mangaId: String, chapterId: String, index: Int, offset: Int, progress: Float, finished: Boolean) =
        viewModelScope.launch {
            library.update { list ->
                list.map { m ->
                    if (m.id != mangaId) m else m.copy(
                        lastRead = System.currentTimeMillis(),
                        lastChapterId = chapterId,
                        chapters = m.chapters.map { c ->
                            if (c.id != chapterId) c else c.copy(
                                posIndex = index,
                                posOffset = offset,
                                progress = maxOf(progress, if (c.read) 1f else 0f),
                                read = c.read || finished,
                            )
                        },
                    )
                }
            }
        }.also { job ->
            synchronized(saves) { saves.removeAll { !it.isActive }; saves += job }
        }
}
