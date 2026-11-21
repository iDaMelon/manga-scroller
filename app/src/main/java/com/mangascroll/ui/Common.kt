package com.mangascroll.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mangascroll.data.ImportSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Returns a function that opens the picker for the chosen import kind. */
@Composable
fun rememberImportPickers(onSource: (ImportSource) -> Unit): (ImportKind) -> Unit {
    val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onSource(ImportSource.Images(uris))
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onSource(ImportSource.Folder(uri))
    }
    val zips = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onSource(ImportSource.Zips(uris))
    }
    return { kind ->
        when (kind) {
            ImportKind.Images -> images.launch(arrayOf("image/*"))
            ImportKind.Folder -> folder.launch(null)
            ImportKind.Zip -> zips.launch(
                arrayOf(
                    "application/zip", "application/x-zip-compressed", "application/x-cbz",
                    "application/vnd.comicbook+zip", "application/octet-stream",
                )
            )
        }
    }
}

enum class ImportKind { Images, Folder, Zip }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(onDismiss: () -> Unit, onPick: (ImportKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Import from",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetOption(Icons.Default.Image, "Images", "Pick the pages — they're sorted by file name (01, 02, … 10)") {
                onPick(ImportKind.Images)
            }
            SheetOption(Icons.Default.Folder, "Folder", "A folder of pages, or a folder with one sub-folder per chapter") {
                onPick(ImportKind.Folder)
            }
            SheetOption(Icons.Default.Archive, "ZIP / CBZ", "One or more archives — each becomes a chapter") {
                onPick(ImportKind.Zip)
            }
        }
    }
}

@Composable
private fun SheetOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Title / chapter-name prompt shown after picking, plus progress and error dialogs. */
@Composable
fun ImportDialogs(vm: AppViewModel) {
    vm.pending?.let { p ->
        val isNew = p.targetMangaId == null
        val askChapter = p.source is ImportSource.Images
        var title by rememberSaveable(p) { mutableStateOf(p.title) }
        var chapter by rememberSaveable(p) { mutableStateOf(p.chapterName) }
        AlertDialog(
            onDismissRequest = { vm.cancelPending() },
            title = { Text(if (isNew) "New manga" else "Add chapter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isNew) {
                        OutlinedTextField(
                            value = title, onValueChange = { title = it },
                            label = { Text("Title") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                    }
                    if (askChapter) {
                        OutlinedTextField(
                            value = chapter, onValueChange = { chapter = it },
                            label = { Text("Chapter name") }, singleLine = true,
                        )
                    } else {
                        Text(
                            "Each sub-folder or archive will become its own chapter.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmImport(title, chapter) }) { Text("Import") } },
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
                        LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth())
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
    var text by rememberSaveable { mutableStateOf(initial) }
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

@Composable
fun CoverImage(file: File, version: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file.path, version) {
        value = withContext(Dispatchers.IO) {
            if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
    }
}
