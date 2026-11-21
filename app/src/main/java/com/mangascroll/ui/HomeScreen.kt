package com.mangascroll.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangascroll.data.Manga

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val all by vm.manga.collectAsState()
    val ready by vm.ready.collectAsState()
    val list = remember(all) { all.sortedByDescending { maxOf(it.lastRead, it.created) } }
    var showSheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Manga?>(null) }
    var deleting by remember { mutableStateOf<Manga?>(null) }
    val pick = rememberImportPickers { vm.beginImport(it, null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MangaScroll", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            if (list.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showSheet = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Import") },
                )
            }
        },
    ) { pad ->
        if (ready && list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.AutoStories, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Your shelf is empty", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Import long-strip pages and they'll be joined into one smooth scroll.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { showSheet = true }) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Import manga")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val recent = list.firstOrNull { it.lastRead > 0 }
                if (recent != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ContinueCard(vm, recent) }
                }
                items(list, key = { it.id }) { m ->
                    MangaTile(
                        vm, m,
                        onOpen = { vm.open(Screen.Detail(m.id)) },
                        onRename = { renaming = m },
                        onDelete = { deleting = m },
                    )
                }
            }
        }
    }

    if (showSheet) ImportSheet(onDismiss = { showSheet = false }) { showSheet = false; pick(it) }
    renaming?.let { m ->
        TextPromptDialog("Rename", m.title, "Title", onDismiss = { renaming = null }) { vm.renameManga(m.id, it) }
    }
    deleting?.let { m ->
        ConfirmDialog("Delete \"${m.title}\"?", "This removes it and all its imported pages from the app.", "Delete",
            onDismiss = { deleting = null }) { vm.deleteManga(m.id) }
    }
}

@Composable
private fun ContinueCard(vm: AppViewModel, m: Manga) {
    val ch = m.continueChapter() ?: return
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(
                vm.library.coverFile(m.id), vm.coverVersion,
                Modifier.width(56.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(6.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Continue reading", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ch.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(onClick = { vm.open(Screen.Reader(m.id, ch.id)) }) {
                Icon(Icons.Default.PlayArrow, null)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaTile(vm: AppViewModel, m: Manga, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            CoverImage(
                vm.library.coverFile(m.id), vm.coverVersion,
                Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)),
            )
            if (m.chapters.isNotEmpty() && m.readCount > 0) {
                LinearProgressIndicator(
                    progress = { m.readCount.toFloat() / m.chapters.size },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                )
            }
            Text(
                m.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "${m.readCount}/${m.chapters.size} ch",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}
