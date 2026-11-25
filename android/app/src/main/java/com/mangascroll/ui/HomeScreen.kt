package com.mangascroll.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
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
    val settings = vm.settings
    var sort by rememberSaveable { mutableStateOf(runCatching { SortMode.valueOf(settings.defaultSort) }.getOrDefault(SortMode.Recent)) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Manga?>(null) }
    var deleting by remember { mutableStateOf<Manga?>(null) }
    val pick = rememberImportPickers { vm.beginImport(it, null) }
    val c = MaterialTheme.colorScheme

    val list = remember(all, sort, query) {
        val filtered = if (query.isBlank()) all else all.filter { it.title.contains(query.trim(), ignoreCase = true) }
        when (sort) {
            SortMode.Recent -> filtered.sortedByDescending { maxOf(it.lastRead, it.created) }
            SortMode.Title -> filtered.sortedWith(compareBy(NaturalOrder) { it.title })
            SortMode.Unread -> filtered.sortedByDescending { it.chapters.size - it.readCount }
            SortMode.Added -> filtered.sortedByDescending { it.created }
        }
    }
    val cell = when (settings.coverSize) { "small" -> 96.dp; "large" -> 150.dp; else -> 118.dp }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(cell),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 10.dp)) {
                    // search pill + settings, like a store's top bar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(50)).background(c.surfaceContainerHigh)
                                .padding(start = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CookieBadge(Icons.AutoMirrored.Rounded.MenuBook, c.primary, c.onPrimary, size = 44.dp, lobes = 10)
                            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                if (query.isEmpty()) {
                                    Text("Search manga", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                                }
                                BasicTextField(
                                    value = query, onValueChange = { query = it }, singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                                    cursorBrush = SolidColor(c.primary),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear", tint = c.onSurfaceVariant) }
                            } else {
                                IconButton(onClick = {
                                    settings.theme = if (c.isDark) "light" else "dark"
                                }) {
                                    Icon(if (c.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "Switch theme", tint = c.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        RoundIconButton(onClick = { vm.open(Screen.Settings) }, container = c.surfaceContainerHigh, size = 52) {
                            Icon(Icons.Rounded.Settings, "Settings")
                        }
                    }
                    Row(
                        Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SortMode.entries.forEach { mode ->
                            val on = sort == mode
                            Text(
                                mode.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (on) c.onPrimary else c.onSurface,
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (on) c.primary else c.surfaceContainerHigh)
                                    .clickable { sort = mode }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                    val chapters = all.sumOf { it.chapters.size }
                    Text(
                        "${all.size} manga · $chapters chapters · ${sort.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 2.dp),
                    )
                }
            }
            if (ready && all.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyLibrary(onImport = { showSheet = true }) }
            } else if (list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Nothing matches \"$query\"",
                        color = c.onSurfaceVariant,
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

        if (all.isNotEmpty()) {
            GradientButton(
                onClick = { showSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(20.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Import")
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
fun RoundIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    size: Int = 48,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(size.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaTile(vm: AppViewModel, m: Manga, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val unread = m.chapters.size - m.readCount
    val progress = if (m.chapters.isEmpty()) 0f else m.readCount.toFloat() / m.chapters.size
    Box {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4.2f).clip(RoundedCornerShape(24.dp))) {
                CoverImage(vm.library.coverFile(m.id), vm.coverVersion, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50))
                        .background(if (unread > 0) c.primary else c.tertiaryContainer)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    if (unread > 0) Text("$unread", style = MaterialTheme.typography.labelMedium, color = c.onPrimary)
                    else Icon(Icons.Rounded.Check, "All read", tint = c.onTertiaryContainer, modifier = Modifier.size(14.dp))
                }
                Text(
                    "${m.chapters.size} ch",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp),
                )
                if (progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.18f)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(4.dp).background(c.primary))
                }
            }
            Text(
                m.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
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
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StackIllustration(Modifier.size(180.dp))
        Spacer(Modifier.height(28.dp))
        Text("Your shelf is empty", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import long-strip pages and they'll be stitched into one smooth scroll.",
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

/** Fanned stack of comic panels, drawn in code with the theme's colors. */
@Composable
private fun StackIllustration(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val back1 = cs.secondaryContainer
    val back2 = cs.tertiaryContainer
    val front = cs.surfaceContainerHighest
    val panel = cs.outlineVariant
    val hero = cs.primary
    Canvas(modifier) {
        val w = size.width * 0.46f
        val h = size.height * 0.78f
        val center = Offset(size.width / 2, size.height / 2)
        val r = CornerRadius(22.dp.toPx())
        rotate(-14f, center) { drawRoundRect(back1, Offset(center.x - w / 2 - size.width * 0.14f, center.y - h / 2), Size(w, h), r) }
        rotate(12f, center) { drawRoundRect(back2, Offset(center.x - w / 2 + size.width * 0.14f, center.y - h / 2), Size(w, h), r) }
        val left = center.x - w / 2
        val top = center.y - h / 2
        drawRoundRect(front, Offset(left, top), Size(w, h), r)
        val pad = w * 0.1f
        val gap = pad * 0.6f
        val heights = listOf(0.28f, 0.38f, 0.22f).map { it * (h - pad * 2 - gap * 2) / 0.88f }
        var y = top + pad
        heights.forEachIndexed { i, ph ->
            drawRoundRect(if (i == 1) hero else panel, Offset(left + pad, y), Size(w - pad * 2, ph), CornerRadius(10.dp.toPx()))
            y += ph + gap
        }
    }
}
