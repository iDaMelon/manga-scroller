package com.mangascroll.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mangascroll.data.Chapter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaScreen(vm: AppViewModel, mangaId: String) {
    val all by vm.manga.collectAsState()
    val m = all.firstOrNull { it.id == mangaId } ?: return
    var menu by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var renamingChapter by remember { mutableStateOf<Chapter?>(null) }
    var deletingChapter by remember { mutableStateOf<Chapter?>(null) }
    val pick = rememberImportPickers { vm.beginImport(it, mangaId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(m.title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Sort chapters by name") }, onClick = { menu = false; vm.sortChapters(mangaId) })
                            DropdownMenuItem(text = { Text("Reverse chapter order") }, onClick = { menu = false; vm.reverseChapters(mangaId) })
                            DropdownMenuItem(text = { Text("Delete manga") }, onClick = { menu = false; deleting = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showSheet = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add chapter") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 96.dp),
        ) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(
                        vm.library.coverFile(m.id), vm.coverVersion,
                        Modifier.width(110.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)),
                    )
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(m.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${m.chapters.size} chapters · ${m.pageCount} pages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        m.continueChapter()?.let { ch ->
                            Button(onClick = { vm.open(Screen.Reader(m.id, ch.id)) }) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (m.lastRead > 0) "Continue · ${ch.name}" else "Start reading", maxLines = 1)
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
            items(m.chapters, key = { it.id }) { c ->
                ChapterRow(
                    c,
                    isLast = c.id == m.lastChapterId,
                    onOpen = { vm.open(Screen.Reader(m.id, c.id)) },
                    onToggleRead = { vm.setRead(m.id, c.id, !c.read) },
                    onRename = { renamingChapter = c },
                    onDelete = { deletingChapter = c },
                )
            }
        }
    }

    if (showSheet) ImportSheet(onDismiss = { showSheet = false }) { showSheet = false; pick(it) }
    if (renaming) TextPromptDialog("Rename", m.title, "Title", onDismiss = { renaming = false }) { vm.renameManga(m.id, it) }
    if (deleting) ConfirmDialog("Delete \"${m.title}\"?", "This removes it and all its imported pages from the app.", "Delete",
        onDismiss = { deleting = false }) { vm.deleteManga(m.id) }
    renamingChapter?.let { c ->
        TextPromptDialog("Rename chapter", c.name, "Name", onDismiss = { renamingChapter = null }) { vm.renameChapter(m.id, c.id, it) }
    }
    deletingChapter?.let { c ->
        ConfirmDialog("Delete \"${c.name}\"?", "Its pages will be removed from the app.", "Delete",
            onDismiss = { deletingChapter = null }) { vm.deleteChapter(m.id, c.id) }
    }
}

@Composable
private fun ChapterRow(
    c: Chapter,
    isLast: Boolean,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = {
            Text(
                c.name,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (c.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            val state = when {
                c.read -> "Read"
                c.progress > 0f -> "${(c.progress * 100).toInt()}% read"
                else -> "New"
            }
            Text("${c.pages.size} pages · $state")
        },
        leadingContent = {
            when {
                c.read -> Icon(Icons.Default.CheckCircle, "Read", tint = MaterialTheme.colorScheme.primary)
                c.progress > 0f -> CircularProgressIndicator(progress = { c.progress }, modifier = Modifier.width(24.dp).aspectRatio(1f), strokeWidth = 3.dp)
                else -> Icon(Icons.Outlined.Circle, null, tint = MaterialTheme.colorScheme.outline)
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Chapter options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (c.read) "Mark as unread" else "Mark as read") }, onClick = { menu = false; onToggleRead() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        },
    )
}
