package com.mangascroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mangascroll.data.Chapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.mangascroll.data.Library
import com.mangascroll.data.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Numbers shown in a chapter's Properties. */
data class ChapterStats(
    val pageBytes: Long,
    val aiBytes: Long,
    val formats: List<String>,
    val minWidth: Int,
    val maxWidth: Int,
    val stripLength: Long,
)

private fun formatName(ext: String) = when (ext.lowercase()) {
    "jpg", "jpeg" -> "JPEG"; "png" -> "PNG"; "webp" -> "WebP"; "gif" -> "GIF"; "bmp" -> "BMP"
    "heic", "heif" -> "HEIF"; "avif" -> "AVIF"; else -> ext.uppercase()
}

suspend fun chapterStats(lib: Library, mangaId: String, c: Chapter): ChapterStats = withContext(Dispatchers.IO) {
    val dir = lib.chapterDir(mangaId, c.id)
    ChapterStats(
        pageBytes = c.pages.sumOf { File(dir, it.file).length() },
        aiBytes = 0L,
        formats = c.pages.map { formatName(it.file.substringAfterLast('.', "")) }.distinct(),
        minWidth = c.pages.minOfOrNull { it.w } ?: 0,
        maxWidth = c.pages.maxOfOrNull { it.w } ?: 0,
        stripLength = c.pages.sumOf { it.h.toLong() },
    )
}

fun formatDate(ms: Long): String =
    if (ms <= 0) "Unknown" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

@Composable
fun ChapterPropertiesDialog(vm: AppViewModel, manga: Manga, chapter: Chapter, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val stats by produceState<ChapterStats?>(null, chapter) { value = chapterStats(vm.library, manga.id, chapter) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.comicbook+zip")) { uri ->
        if (uri != null) vm.exportChapter(manga.id, chapter.id, uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Column {
                Text(chapter.name, style = MaterialTheme.typography.headlineSmall)
                Text(manga.title, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ---------- storage ----------
                PropSection(Icons.Rounded.Storage, "Storage") {
                    val s = stats
                    if (s == null) {
                        Text("Measuring…", color = c.onSurfaceVariant)
                    } else {
                        PropRow("Size", formatSize(s.pageBytes), strong = true)
                        PropRow("Pages", "${chapter.pages.size}")
                        PropRow("Format", s.formats.joinToString(", "))
                        PropRow("Page width", if (s.minWidth == s.maxWidth) "${s.maxWidth} px" else "${s.minWidth}–${s.maxWidth} px")
                        PropRow("Strip length", "%,d px".format(s.stripLength))
                    }
                }

                // ---------- imported ----------
                PropSection(Icons.Rounded.CalendarMonth, "Imported") {
                    Text(formatDate(chapter.added), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            OutlinedButton(onClick = {
                saveAs.launch("${manga.title} - ${chapter.name}.cbz".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
            }) {
                Icon(Icons.Rounded.Unarchive, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Export as CBZ")
            }
        },
    )
}

@Composable
private fun PropSection(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun PropRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (strong) MaterialTheme.colorScheme.onSurface else Color.Unspecified)
    }
}
