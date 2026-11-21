package com.mangascroll.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangascroll.data.ImportSource
import com.mangascroll.data.Importer
import com.mangascroll.data.Library
import com.mangascroll.data.Manga
import com.mangascroll.data.NaturalOrder
import com.mangascroll.data.newId
import kotlinx.coroutines.Job
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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val library = Library.get(app)
    private val importer = Importer(app, library)

    val manga = library.items
    val ready = library.ready

    val stack = mutableStateListOf<Screen>(Screen.Home)

    var pending by mutableStateOf<PendingImport?>(null)
        private set

    private val _status = MutableStateFlow<ImportStatus?>(null)
    val status = _status.asStateFlow()
    private var importJob: Job? = null

    /** Bumped when a cover image changes so the home grid reloads it. */
    var coverVersion by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch { library.load() }
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
            kotlinx.coroutines.Dispatchers.IO.let { withContext(it) { library.mangaDir(mangaId).deleteRecursively() } }
        }
    }

    fun cancelImport() { importJob?.cancel() }
    fun dismissStatus() { _status.value = null }

    // ---------- edits ----------

    fun renameManga(id: String, title: String) = viewModelScope.launch {
        library.updateManga(id) { it.copy(title = title.ifBlank { it.title }) }
    }

    fun deleteManga(id: String) = viewModelScope.launch {
        stack.removeAll { (it is Screen.Detail && it.mangaId == id) || (it is Screen.Reader && it.mangaId == id) }
        library.deleteManga(id)
    }

    fun renameChapter(mangaId: String, chapterId: String, name: String) = viewModelScope.launch {
        library.updateChapter(mangaId, chapterId) { it.copy(name = name.ifBlank { it.name }) }
    }

    fun deleteChapter(mangaId: String, chapterId: String) = viewModelScope.launch {
        val wasFirst = library.find(mangaId)?.chapters?.firstOrNull()?.id == chapterId
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
        }
}
