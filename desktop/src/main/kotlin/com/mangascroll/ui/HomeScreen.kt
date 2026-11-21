package com.mangascroll.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangascroll.data.Manga
import com.mangascroll.data.NaturalOrder

private enum class SortMode(val label: String) { Recent("Recent"), Title("A–Z"), Unread("Unread"), Added("Added") }

@Composable
fun HomeScreen(vm: AppViewModel) {
    val all by vm.manga.collectAsState()
    val ready by vm.ready.collectAsState()
    var sort by rememberSaveable { mutableStateOf(SortMode.Recent) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Manga?>(null) }
    var deleting by remember { mutableStateOf<Manga?>(null) }

    val list = remember(all, sort, query) {
        val filtered = if (query.isBlank()) all else all.filter { it.title.contains(query.trim(), ignoreCase = true) }
        when (sort) {
            SortMode.Recent -> filtered.sortedByDescending { maxOf(it.lastRead, it.created) }
            SortMode.Title -> filtered.sortedWith(compareBy(NaturalOrder) { it.title })
            SortMode.Unread -> filtered.sortedByDescending { it.chapters.size - it.readCount }
            SortMode.Added -> filtered.sortedByDescending { it.created }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (ready && all.isEmpty()) {
            EmptyLibrary(onImport = { showSheet = true })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 28.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Library", style = MaterialTheme.typography.displaySmall)
                                val chapters = all.sumOf { it.chapters.size }
                                Text(
                                    "${all.size} ${if (all.size == 1) "title" else "titles"} · $chapters chapters",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RoundIconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                                Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Search")
                            }
                        }
                        AnimatedVisibility(searching, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it },
                                placeholder = { Text("Search titles") },
                                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                singleLine = true,
                                shape = RoundedCornerShape(50),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedBorderColor = Color.Transparent,
                                ),
                                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = 14.dp),
                            )
                        }
                        Row(
                            Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SortMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = sort == mode,
                                    onClick = { sort = mode },
                                    label = { Text(mode.label) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Palette.Coral.copy(alpha = 0.18f),
                                        selectedLabelColor = Palette.Coral,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true, selected = sort == mode,
                                        borderColor = MaterialTheme.colorScheme.outline,
                                        selectedBorderColor = Palette.Coral.copy(alpha = 0.5f),
                                    ),
                                )
                            }
                        }
                    }
                }
                if (list.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Nothing matches \"$query\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
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

            GradientButton(
                onClick = { showSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(28.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Import")
            }
        }
    }

    if (showSheet) ImportSheet(onDismiss = { showSheet = false }) { kind -> showSheet = false; Pickers.pick(kind)?.let { vm.beginImport(it, null) } }
    renaming?.let { m ->
        TextPromptDialog("Rename", m.title, "Title", onDismiss = { renaming = null }) { vm.renameManga(m.id, it) }
    }
    deleting?.let { m ->
        ConfirmDialog("Delete \"${m.title}\"?", "This removes it and all its imported pages from the app.", "Delete",
            onDismiss = { deleting = null }) { vm.deleteManga(m.id) }
    }
}

@Composable
fun RoundIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceVariant, content: @Composable () -> Unit) {
    Box(modifier.size(44.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaTile(vm: AppViewModel, m: Manga, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val unread = m.chapters.size - m.readCount
    val progress = if (m.chapters.isEmpty()) 0f else m.readCount.toFloat() / m.chapters.size
    Box {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4.2f).clip(RoundedCornerShape(16.dp))) {
                CoverImage(vm.library.coverFile(m.id), vm.coverVersion, Modifier.fillMaxSize())
                // bottom scrim so the progress line and badge stay readable
                Box(
                    Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                )
                // badge: unread count, or a check when everything is read
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50))
                        .background(if (unread > 0) Palette.SunsetH else Brush.linearGradient(listOf(Palette.Mint, Palette.Mint)))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    if (unread > 0) Text("$unread", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    else Icon(Icons.Rounded.Check, "All read", tint = Color(0xFF0B3D2E), modifier = Modifier.size(14.dp))
                }
                Text(
                    "${m.chapters.size} ch",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 10.dp),
                )
                if (progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.15f)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(3.dp).background(Palette.SunsetH))
                }
            }
            Text(
                m.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
internal fun EmptyLibrary(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StackIllustration(Modifier.size(180.dp))
        Spacer(Modifier.height(28.dp))
        Text("Your shelf is empty", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import long-strip pages, or drag them onto this window, and they'll be stitched into one smooth scroll.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton(onClick = onImport) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Import manga")
        }
    }
}

/** Fanned stack of comic panels, drawn in code. */
@Composable
private fun StackIllustration(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width * 0.46f
        val h = size.height * 0.78f
        val c = Offset(size.width / 2, size.height / 2)
        val r = CornerRadius(18.dp.toPx())
        fun card(angle: Float, dx: Float, brush: Brush) = rotate(angle, c) {
            drawRoundRect(brush, Offset(c.x - w / 2 + dx, c.y - h / 2), Size(w, h), r)
        }
        card(-14f, -size.width * 0.14f, Brush.linearGradient(listOf(Palette.Violet.copy(alpha = 0.55f), Palette.Violet.copy(alpha = 0.25f))))
        card(12f, size.width * 0.14f, Brush.linearGradient(listOf(Palette.Amber.copy(alpha = 0.55f), Palette.Amber.copy(alpha = 0.25f))))
        // front card with panel lines
        val left = c.x - w / 2
        val top = c.y - h / 2
        drawRoundRect(Palette.CardHigh, Offset(left, top), Size(w, h), r)
        val pad = w * 0.1f
        val panelW = w - pad * 2
        val gap = pad * 0.6f
        val heights = listOf(0.28f, 0.38f, 0.22f).map { it * (h - pad * 2 - gap * 2) / 0.88f }
        var y = top + pad
        heights.forEachIndexed { i, ph ->
            drawRoundRect(
                if (i == 1) Palette.Sunset else Brush.linearGradient(listOf(Palette.Line, Palette.Line)),
                Offset(left + pad, y), Size(panelW, ph), CornerRadius(8.dp.toPx()),
            )
            y += ph + gap
        }
    }
}
