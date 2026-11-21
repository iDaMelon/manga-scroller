package com.mangascroll.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mangascroll.data.ImportSource
import com.mangascroll.data.isImageName
import com.mangascroll.data.isZipName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

enum class ImportKind { Images, Folder, Zip }

/** Native file dialogs. Returns null if the user cancels. */
object Pickers {
    var owner: Frame? = null

    init {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    }

    private fun pickFiles(title: String, accept: (String) -> Boolean): List<File>? {
        val dialog = FileDialog(owner, title, FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> accept(name) }
        }
        dialog.isVisible = true
        return dialog.files?.toList()?.filter { accept(it.name) }?.takeIf { it.isNotEmpty() }
    }

    private fun pickFolder(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a folder of pages"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun pick(kind: ImportKind): ImportSource? = when (kind) {
        ImportKind.Images -> pickFiles("Choose pages", ::isImageName)?.let { ImportSource.Images(it) }
        ImportKind.Folder -> pickFolder()?.let { ImportSource.Folder(it) }
        ImportKind.Zip -> pickFiles("Choose ZIP / CBZ files", ::isZipName)?.let { ImportSource.Zips(it) }
    }
}

/** Centered import chooser shown over the window. */
@Composable
fun ImportSheet(onDismiss: () -> Unit, onPick: (ImportKind) -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 460.dp).padding(24.dp).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Import", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pages are sorted by file name, so 01, 02 … 10 stay in order. You can also drag files or a folder onto the window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            SheetOption(Icons.Rounded.Image, Palette.Coral, "Images", "Pick the pages of one chapter") { onPick(ImportKind.Images) }
            SheetOption(Icons.Rounded.Folder, Palette.Amber, "Folder", "A folder of pages, or one sub-folder per chapter") { onPick(ImportKind.Folder) }
            SheetOption(Icons.Rounded.Archive, Palette.Violet, "ZIP / CBZ", "One or more archives, each becomes a chapter") { onPick(ImportKind.Zip) }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
        }
    }
}

@Composable
private fun SheetOption(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Title / chapter-name prompt shown after picking, plus progress and error dialogs. */
@Composable
fun ImportDialogs(vm: AppViewModel) {
    vm.pending?.let { p ->
        val isNew = p.targetMangaId == null
        val askChapter = p.source is ImportSource.Images
        var title by remember(p) { mutableStateOf(p.title) }
        var chapter by remember(p) { mutableStateOf(p.chapterName) }
        AlertDialog(
            onDismissRequest = { vm.cancelPending() },
            title = { Text(if (isNew) "New manga" else "Add chapter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isNew) {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    }
                    if (askChapter) {
                        OutlinedTextField(value = chapter, onValueChange = { chapter = it }, label = { Text("Chapter name") }, singleLine = true)
                    } else {
                        Text("Each sub-folder or archive will become its own chapter.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { GradientButton(onClick = { vm.confirmImport(title, chapter) }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { vm.cancelPending() }) { Text("Cancel") } },
        )
    }

    val status by vm.status.collectAsState()
    when (val s = status) {
        is ImportStatus.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing…") },
            text = {
                Column {
                    if (s.total > 0) {
                        LinearProgressIndicator(
                            progress = { s.done.toFloat() / s.total },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            gapSize = 0.dp, drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${s.done} of ${s.total} images")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(if (s.done > 0) "${s.done} images" else "Reading files…")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.cancelImport() }) { Text("Cancel") } },
        )
        is ImportStatus.Failed -> AlertDialog(
            onDismissRequest = { vm.dismissStatus() },
            title = { Text("Import failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { vm.dismissStatus() }) { Text("OK") } },
        )
        null -> Unit
    }
}

@Composable
fun TextPromptDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small in-memory cache so covers show instantly when coming back to a screen. */
object CoverCache {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 128
    }
    private fun key(file: File, version: Int) = "${file.path}#$version#${file.lastModified()}"
    fun get(file: File, version: Int): ImageBitmap? = synchronized(cache) { cache[key(file, version)] }
    fun load(file: File, version: Int): ImageBitmap? = get(file, version) ?: runCatching {
        if (!file.exists()) null
        else org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    }.getOrNull()?.also { synchronized(cache) { cache[key(file, version)] = it } }
}

@Composable
fun CoverImage(file: File, version: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState(CoverCache.get(file, version), file.path, version) {
        if (value == null) value = withContext(Dispatchers.IO) { CoverCache.load(file, version) }
    }
    Box(
        modifier.background(Brush.linearGradient(listOf(Palette.CardHigh, Palette.Card))),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = Palette.TextDim.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
        }
    }
}
