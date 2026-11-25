package com.mangascroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.mangascroll.data.Enhanced
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var propsChapter by remember { mutableStateOf<Chapter?>(null) }
    val cover = vm.library.coverFile(m.id)
    val aiStatus by vm.enhancer.status.collectAsState()
    val aiVersion by vm.enhancer.version.collectAsState()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // ---------- hero ----------
            item {
                Box(Modifier.fillMaxWidth()) {
                    // blurred cover as backdrop (blur needs Android 12+, otherwise just dimmed)
                    CoverImage(
                        cover, vm.coverVersion,
                        Modifier.matchParentSize().blur(28.dp),
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
                    Row(
                        Modifier.widthIn(max = 960.dp).fillMaxWidth().align(Alignment.Center)
                            .padding(top = 80.dp, start = 32.dp, end = 32.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        CoverImage(
                            cover, vm.coverVersion,
                            Modifier.width(200.dp).aspectRatio(3f / 4.2f)
                                .shadow(24.dp, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        Column(Modifier.padding(start = 28.dp).weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatPill("${m.chapters.size} chapters")
                                StatPill("${m.pageCount} pages")
                                StatPill("${m.readCount} read")
                            }
                            Spacer(Modifier.height(20.dp))
                            m.continueChapter()?.let { ch ->
                                GradientButton(onClick = { vm.open(Screen.Reader(m.id, ch.id)) }) {
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
            }

            item { AiCard(vm, m) }

            // ---------- chapters header ----------
            item {
                Row(
                    Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(start = 36.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
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
                val ai = when {
                    aiStatus?.let { it.mangaId == m.id && it.chapterId == c.id } == true ->
                        aiStatus!!.let { if (it.total > 0) "AI ${it.done * 100 / it.total}%" else "AI …" }
                    c.aiReplaced || remember(c.id, aiVersion) { Enhanced.isDone(vm.library, m.id, c.id) } -> "AI"
                    else -> null
                }
                ChapterRow(
                    ai = ai,
                    number = i + 1,
                    c = c,
                    isCurrent = c.id == m.lastChapterId,
                    onOpen = { vm.open(Screen.Reader(m.id, c.id)) },
                    onToggleRead = { vm.setRead(m.id, c.id, !c.read) },
                    onRename = { renamingChapter = c },
                    onDelete = { deletingChapter = c },
                    onProperties = { propsChapter = c },
                )
            }
        }

        // ---------- floating top controls ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
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

    if (showSheet) ImportSheet(onDismiss = { showSheet = false }) { kind -> showSheet = false; Pickers.pick(kind)?.let { vm.beginImport(it, mangaId) } }
    if (renaming) TextPromptDialog("Rename", m.title, "Title", onDismiss = { renaming = false }) { vm.renameManga(m.id, it) }
    if (deleting) ConfirmDialog("Delete \"${m.title}\"?", "This removes it and all its imported pages from the app.", "Delete",
        onDismiss = { deleting = false }) { vm.deleteManga(m.id) }
    renamingChapter?.let { c ->
        TextPromptDialog("Rename chapter", c.name, "Name", onDismiss = { renamingChapter = null }) { vm.renameChapter(m.id, c.id, it) }
    }
    propsChapter?.let { pc ->
        // show the latest copy of the chapter (it can change while the dialog is open)
        val live = m.chapters.firstOrNull { it.id == pc.id }
        if (live == null) propsChapter = null
        else ChapterPropertiesDialog(vm, m, live) { propsChapter = null }
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
        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ChapterRow(
    ai: String?,
    number: Int,
    c: Chapter,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .widthIn(max = 960.dp)
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 4.dp)
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
                    Modifier.size(40.dp).clip(CookieShape(9, 0.12f)).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, "Read", tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp)) }
                c.progress > 0f -> {
                    CircularProgressIndicator(
                        progress = { c.progress }, modifier = Modifier.size(36.dp), strokeWidth = 3.dp,
                        color = Palette.Coral, trackColor = MaterialTheme.colorScheme.outline,
                    )
                    Text("$number", style = MaterialTheme.typography.labelMedium)
                }
                else -> Box(
                    Modifier.size(40.dp).clip(CookieShape(9, 0.12f)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer) }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${c.pages.size} pages · $state",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!c.read && c.progress == 0f) Palette.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ai != null) {
                    Text(
                        ai,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ai == "AI") MaterialTheme.colorScheme.onPrimary else Palette.Amber,
                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
                            .background(if (ai == "AI") Palette.SunsetH else Brush.linearGradient(listOf(Palette.Amber.copy(alpha = 0.15f), Palette.Amber.copy(alpha = 0.15f))))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "Chapter options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (c.read) "Mark as unread" else "Mark as read") }, onClick = { menu = false; onToggleRead() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Properties") }, onClick = { menu = false; onProperties() })
                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}


@Composable
private fun AiCard(vm: AppViewModel, m: com.mangascroll.data.Manga) {
    val status by vm.enhancer.status.collectAsState()
    val version by vm.enhancer.version.collectAsState()
    val done = remember(m.chapters, version) { m.chapters.count { it.aiReplaced || Enhanced.isDone(vm.library, m.id, it.id) } }
    val bytes by produceState(0L, m.id, version) {
        value = withContext(Dispatchers.IO) { Enhanced.sizeBytes(vm.library, m.id) }
    }
    val mine = status?.takeIf { it.mangaId == m.id }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.Amber)
            Spacer(Modifier.width(10.dp))
            Text("AI enhance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (vm.enhancer.available) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("off" to "Off", "reading" to "As you read", "all" to "Whole manga").forEach { (mode, label) ->
                        val selected = m.enhance == mode
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (selected) Palette.SunsetH else Brush.linearGradient(listOf(Palette.CardHigh, Palette.CardHigh)))
                                .clickable { vm.setEnhanceMode(m.id, mode) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val explain = when {
            !vm.enhancer.available -> "The AI engine wasn't found. It comes in the \"ai\" folder of the Windows download."
            m.enhance == "reading" -> "Enhances the chapter you're reading and the next 2 on your graphics card, and clears older ones."
            m.enhance == "all" -> "Enhancing every chapter in the background. Uses about 1.5x the space of the originals."
            else -> "Redraws pages at 1.5x with sharper lines and less JPEG blockiness, using your graphics card. Your original files are kept."
        }
        Text(explain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (vm.enhancer.available && (mine != null || done > 0)) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (mine != null) {
                        val pct = if (mine.total > 0) mine.done * 100 / mine.total else 0
                        Text(
                            "Enhancing ${mine.chapterName} · $pct%" + if (mine.queued > 0) " · ${mine.queued} more queued" else "",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Palette.Line)) {
                            Box(Modifier.fillMaxWidth(pct / 100f).height(4.dp).background(Palette.SunsetH))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        "$done of ${m.chapters.size} chapters enhanced · ${formatBytes(bytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (done > 0) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete enhanced pages") }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete enhanced pages?",
            "Frees ${formatBytes(bytes)}. Your original pages stay, and AI enhance is turned off for this manga.",
            "Delete",
            onDismiss = { confirmDelete = false },
        ) { vm.deleteEnhanced(m.id) }
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "%.0f KB".format(b / 1024.0)
}
