package com.mangascroll.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangascroll.data.Chapter

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
    val cover = vm.library.coverFile(m.id)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // ---------- hero ----------
            item {
                Box(Modifier.fillMaxWidth()) {
                    // blurred cover as backdrop (blur needs Android 12+, otherwise just dimmed)
                    CoverImage(
                        cover, vm.coverVersion,
                        Modifier.matchParentSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier),
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                1f to MaterialTheme.colorScheme.background,
                            )
                        )
                    )
                    Column(
                        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 64.dp, start = 20.dp, end = 20.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CoverImage(
                            cover, vm.coverVersion,
                            Modifier.width(150.dp).aspectRatio(3f / 4.2f)
                                .shadow(24.dp, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(m.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPill("${m.chapters.size} chapters")
                            StatPill("${m.pageCount} pages")
                            StatPill("${m.readCount} read")
                        }
                        Spacer(Modifier.height(20.dp))
                        m.continueChapter()?.let { ch ->
                            GradientButton(onClick = { vm.open(Screen.Reader(m.id, ch.id)) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (m.lastRead > 0) "Continue · ${ch.name}" else "Start reading",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // ---------- chapters header ----------
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Chapters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.reverseChapters(mangaId) }) { Icon(Icons.Rounded.SwapVert, "Reverse order") }
                    TextButton(onClick = { showSheet = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add")
                    }
                }
            }

            itemsIndexed(m.chapters, key = { _, c -> c.id }) { i, c ->
                ChapterRow(
                    number = i + 1,
                    c = c,
                    isCurrent = c.id == m.lastChapterId,
                    onOpen = { vm.open(Screen.Reader(m.id, c.id)) },
                    onToggleRead = { vm.setRead(m.id, c.id, !c.read) },
                    onRename = { renamingChapter = c },
                    onDelete = { deletingChapter = c },
                )
            }
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
        }

        // ---------- floating top controls ----------
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(onClick = { vm.back() }, container = Color.Black.copy(alpha = 0.35f)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Box {
                RoundIconButton(onClick = { menu = true }, container = Color.Black.copy(alpha = 0.35f)) {
                    Icon(Icons.Rounded.MoreVert, "More", tint = Color.White)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Add chapters") }, onClick = { menu = false; showSheet = true })
                    DropdownMenuItem(text = { Text("Sort chapters by name") }, onClick = { menu = false; vm.sortChapters(mangaId) })
                    DropdownMenuItem(text = { Text("Delete manga", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
                }
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
private fun StatPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ChapterRow(
    number: Int,
    c: Chapter,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (isCurrent) Modifier.border(1.dp, Palette.Coral.copy(alpha = 0.6f), shape) else Modifier)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // number badge / progress ring
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            when {
                c.read -> Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Palette.Mint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, "Read", tint = Palette.Mint, modifier = Modifier.size(20.dp)) }
                c.progress > 0f -> {
                    CircularProgressIndicator(
                        progress = { c.progress }, modifier = Modifier.size(36.dp), strokeWidth = 3.dp,
                        color = Palette.Coral, trackColor = MaterialTheme.colorScheme.outline,
                    )
                    Text("$number", style = MaterialTheme.typography.labelMedium)
                }
                else -> Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Text("$number", style = MaterialTheme.typography.labelMedium) }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                c.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (c.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val state = when {
                c.read -> "Read"
                c.progress > 0f -> "${(c.progress * 100).toInt()}% read"
                else -> "New"
            }
            Text(
                "${c.pages.size} pages · $state",
                style = MaterialTheme.typography.bodySmall,
                color = if (!c.read && c.progress == 0f) Palette.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "Chapter options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (c.read) "Mark as unread" else "Mark as read") }, onClick = { menu = false; onToggleRead() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
