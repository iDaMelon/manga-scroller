package com.mangascroll.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette as PaletteIcon
import androidx.compose.material.icons.rounded.PhotoSizeSelectActual
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ======================================================================
// Settings home: quick toggles + category cards
// ======================================================================

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val all by vm.manga.collectAsState()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val chapters = all.sumOf { it.chapters.size }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp, bottom = 8.dp)) {
                RoundIconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.height(18.dp))
                Text("Settings", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(start = 12.dp))
                Text(
                    (if (version.isBlank()) "MangaScroll" else "MangaScroll v$version") + " · ${all.size} manga · $chapters chapters",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }
        }

        item { SectionLabel("Quick toggles") }
        item {
            val toggles = buildList {
                add(Triple("Keep screen on", Icons.Rounded.LightMode, s.keepScreenOn) to { s.keepScreenOn = !s.keepScreenOn })
                add(Triple("Sharper pages", Icons.Rounded.AutoFixHigh, s.sharper) to { s.sharper = !s.sharper })
                add(Triple("Auto next chapter", Icons.Rounded.SkipNext, s.autoNext) to { s.autoNext = !s.autoNext })
                add(Triple("Page gaps", Icons.Rounded.ViewAgenda, s.pageGaps) to { s.pageGaps = !s.pageGaps })
                add(Triple("Original size", Icons.Rounded.PhotoSizeSelectActual, s.pageWidth == "original") to {
                    s.pageWidth = if (s.pageWidth == "original") "fit" else "original"
                })
                if (Build.VERSION.SDK_INT >= 31) {
                    add(Triple("Material You", Icons.Rounded.Wallpaper, s.materialYou) to { s.materialYou = !s.materialYou })
                } else {
                    add(Triple("Black theme", Icons.Rounded.Contrast, s.theme == "black") to {
                        s.theme = if (s.theme == "black") "dark" else "black"
                    })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                toggles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (t, onClick) ->
                            QuickToggle(t.first, t.second, t.third, onClick, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item { SectionLabel("Everything else") }
        item {
            CategoryCard(
                seed = Color(0xFF3B82F6), icon = Icons.AutoMirrored.Rounded.MenuBook,
                title = "Reader", subtitle = "Background, page width, gaps and chapters",
                status = buildString {
                    append(when (s.readerBg) { "white" -> "White"; "gray" -> "Gray"; else -> "Black" })
                    append(" · ")
                    append(if (s.pageWidth == "original") "Original size" else "Fit width")
                },
                modifier = Modifier.fillMaxWidth().height(170.dp),
            ) { vm.open(Screen.SettingsPage("reader")) }
        }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryCard(
                    seed = Color(0xFFA855F7), icon = Icons.Rounded.PaletteIcon,
                    title = "Look & feel", subtitle = "Theme, colors and font",
                    status = "${FontChoices.first { it.first == s.font }.second} · ${themeLabel(s.theme)}",
                    extra = { AccentPreview(s) },
                    modifier = Modifier.weight(1f).heightIn(min = 300.dp).fillMaxHeight(),
                ) { vm.open(Screen.SettingsPage("look")) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryCard(
                        seed = Color(0xFF06B6D4), icon = Icons.Rounded.GridView,
                        title = "Library", subtitle = null,
                        status = "${s.coverSize.replaceFirstChar { it.uppercase() }} covers",
                        modifier = Modifier.fillMaxWidth().height(158.dp),
                    ) { vm.open(Screen.SettingsPage("library")) }
                    CategoryCard(
                        seed = Color(0xFFF97316), icon = Icons.Rounded.Storage,
                        title = "Storage", subtitle = null, status = "Space & covers",
                        modifier = Modifier.fillMaxWidth().height(158.dp),
                    ) { vm.open(Screen.SettingsPage("storage")) }
                }
            }
        }
        item {
            CategoryCard(
                seed = Color(0xFF22C55E), icon = Icons.Rounded.Info,
                title = "About", subtitle = "Version, source code and licenses", status = null,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            ) { vm.open(Screen.SettingsPage("about")) }
        }
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

private fun themeLabel(t: String) = when (t) {
    "light" -> "Light"; "dark" -> "Dark"; "black" -> "Black"; else -> "System"
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** Big rounded tile that fills with the accent color when on. */
@Composable
fun QuickToggle(title: String, icon: ImageVector, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (on) c.primary else c.surfaceContainerHigh, label = "toggle")
    val fg by animateColorAsState(if (on) c.onPrimary else c.onSurface, label = "toggleFg")
    Row(
        modifier.heightIn(min = 76.dp).clip(RoundedCornerShape(if (on) 38.dp else 26.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(26.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (on) "On" else "Off", style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.8f))
        }
    }
}

/** Colors for a category card, generated from its own seed so they work in light and dark. */
@Composable
private fun categoryColors(seed: Color): Triple<Color, Color, Color> {
    val dark = MaterialTheme.colorScheme.isDark
    val black = MaterialTheme.colorScheme.background == Color.Black
    return remember(seed, dark, black) {
        val sc = dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false, style = PaletteStyle.TonalSpot)
        val container = if (black) sc.primaryContainer.copy(alpha = 0.55f) else sc.primaryContainer
        Triple(container, sc.onPrimaryContainer, sc.primary)
    }
}

/** Pastel card with a scalloped icon badge and a soft blob in the corner. */
@Composable
fun CategoryCard(
    seed: Color,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    status: String?,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val (container, onContainer, accent) = categoryColors(seed)
    Box(modifier.clip(RoundedCornerShape(32.dp)).background(container).clickable(onClick = onClick)) {
        CornerBlob(
            accent.copy(alpha = 0.10f),
            Modifier.size(170.dp).align(Alignment.BottomEnd).offset(x = 50.dp, y = 40.dp),
        )
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            CookieBadge(icon, accent.copy(alpha = 0.16f), accent, size = 50.dp)
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = onContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = onContainer.copy(alpha = 0.8f))
            }
            extra?.let { Spacer(Modifier.height(10.dp)); it() }
            if (status != null) {
                Spacer(Modifier.height(6.dp))
                Text(status, style = MaterialTheme.typography.titleSmall, color = accent, maxLines = 2)
            }
        }
    }
}

@Composable
private fun AccentPreview(s: AppSettings) {
    val c = MaterialTheme.colorScheme
    Row {
        listOf(c.primary, c.secondary, c.tertiary).forEachIndexed { i, col ->
            Box(
                Modifier.offset(x = (-10 * i).dp).size(34.dp).clip(CircleShape)
                    .border(3.dp, c.surface, CircleShape).background(col)
            )
        }
    }
}

// ======================================================================
// Sub-pages
// ======================================================================

@Composable
fun SettingsPageScreen(vm: AppViewModel, page: String) {
    val (title, sub) = when (page) {
        "look" -> "Look & feel" to "Theme, accent color and font"
        "reader" -> "Reader" to "How pages are shown while reading"
        "library" -> "Library" to "Your home shelf"
        "storage" -> "Storage" to "Space used by your manga"
        else -> "About" to "MangaScroll"
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp, bottom = 4.dp)) {
                RoundIconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.height(18.dp))
                Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 12.dp))
                Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp))
            }
        }
        when (page) {
            "look" -> item { LookPage(vm.settings) }
            "reader" -> item { ReaderPage(vm.settings) }
            "library" -> item { LibraryPage(vm.settings) }
            "storage" -> item { StoragePage(vm) }
            else -> item { AboutPage() }
        }
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
fun SettingGroup(title: String?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 8.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp))
        }
        content()
    }
}

@Composable
fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            thumbContent = if (checked) ({ Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }) else null,
        )
    }
}

/** Row of pill choices; the selected one fills with the accent color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoicePills(options: List<Pair<String, String>>, selected: String, icons: List<ImageVector>? = null, onSelect: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, (id, label) ->
            val on = id == selected
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) c.primary else c.surfaceContainerHighest)
                    .clickable { onSelect(id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icons?.getOrNull(i)?.let {
                    Icon(it, null, tint = if (on) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onPrimary else c.onSurface)
            }
        }
    }
}

@Composable
private fun RowLabel(text: String, sub: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPage(s: AppSettings) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingGroup("Theme") {
            ChoicePills(
                listOf("system" to "System", "light" to "Light", "dark" to "Dark", "black" to "Black"), s.theme,
                icons = listOf(Icons.Rounded.SettingsBrightness, Icons.Rounded.LightMode, Icons.Rounded.DarkMode, Icons.Rounded.Contrast),
            ) { s.theme = it }
            Text("Black uses pure black backgrounds, which saves battery on OLED screens.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        SettingGroup("Accent color") {
            if (Build.VERSION.SDK_INT >= 31) {
                ToggleRow(Icons.Rounded.Wallpaper, "Material You", "Use colors from your wallpaper", s.materialYou) { s.materialYou = it }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Accents.forEachIndexed { i, a ->
                    val on = !s.materialYou && s.accent == i
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                        Box(
                            Modifier.size(52.dp).clip(CookieShape(8, if (on) 0.14f else 0f))
                                .background(a.seed).clickable { s.accent = i; s.materialYou = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                        Text(a.name, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        SettingGroup("Font") {
            FontChoices.forEach { (id, name) ->
                val on = s.font == id
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp))
                        .background(if (on) c.primaryContainer else Color.Transparent)
                        .clickable { s.font = id }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.TextFields, null, tint = if (on) c.onPrimaryContainer else c.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = familyFor(id)), color = if (on) c.onPrimaryContainer else c.onSurface)
                        Text("Chapter 12 · The Last Signal", style = MaterialTheme.typography.bodySmall.copy(fontFamily = familyFor(id)),
                            color = if (on) c.onPrimaryContainer.copy(alpha = 0.8f) else c.onSurfaceVariant)
                    }
                    if (on) Icon(Icons.Rounded.Check, null, tint = c.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ReaderPage(s: AppSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingGroup("Pages") {
            RowLabel("Background", "Color around and between pages")
            ChoicePills(listOf("black" to "Black", "gray" to "Gray", "white" to "White"), s.readerBg,
                icons = listOf(Icons.Rounded.FormatColorFill, Icons.Rounded.FormatColorFill, Icons.Rounded.FormatColorFill)) { s.readerBg = it }
            RowLabel("Page width", "Original size shows every pixel sharp, but pages are narrower")
            ChoicePills(listOf("fit" to "Fit screen", "original" to "Original size"), s.pageWidth,
                icons = listOf(Icons.Rounded.Image, Icons.Rounded.PhotoSizeSelectActual)) { s.pageWidth = it }
            ToggleRow(Icons.Rounded.ViewAgenda, "Gaps between pages", "A small space where one page ends", s.pageGaps) { s.pageGaps = it }
            ToggleRow(Icons.Rounded.AutoFixHigh, "Sharper pages", "Higher-quality resizing with light sharpening", s.sharper) { s.sharper = it }
        }
        SettingGroup("Reading") {
            ToggleRow(Icons.Rounded.SkipNext, "Auto next chapter", "Open the next chapter when you reach the end", s.autoNext) { s.autoNext = it }
            ToggleRow(Icons.Rounded.LightMode, "Keep screen on", "While the reader is open", s.keepScreenOn) { s.keepScreenOn = it }
        }
    }
}

@Composable
private fun LibraryPage(s: AppSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingGroup("Covers") {
            RowLabel("Cover size")
            ChoicePills(listOf("small" to "Small", "medium" to "Medium", "large" to "Large"), s.coverSize,
                icons = listOf(Icons.Rounded.GridView, Icons.Rounded.GridView, Icons.Rounded.GridView)) { s.coverSize = it }
            Spacer(Modifier.height(6.dp))
        }
        SettingGroup("Sorting") {
            RowLabel("Default sort", "How the library is ordered when the app opens")
            ChoicePills(listOf("Recent" to "Recent", "Title" to "A–Z", "Unread" to "Unread", "Added" to "Added"), s.defaultSort,
                icons = List(4) { Icons.AutoMirrored.Rounded.Sort }) { s.defaultSort = it }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StoragePage(vm: AppViewModel) {
    val all by vm.manga.collectAsState()
    val bytes by produceState(-1L, all.size) {
        value = withContext(Dispatchers.IO) { vm.library.root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }
    val free = remember { vm.library.root.usableSpace }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingGroup(null) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CookieBadge(Icons.Rounded.Storage, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, size = 64.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(if (bytes < 0) "Measuring…" else formatSize(bytes), style = MaterialTheme.typography.headlineMedium)
                    Text("${all.size} manga · ${all.sumOf { it.pageCount }} pages", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${formatSize(free)} free on this device", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SettingGroup("Maintenance") {
            Row(
                Modifier.fillMaxWidth().clickable { vm.rebuildCovers() }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Rebuild covers", style = MaterialTheme.typography.titleMedium)
                    Text("Make every cover again from its first page", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AboutPage() {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingGroup(null) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CookieBadge(Icons.AutoMirrored.Rounded.MenuBook, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, size = 64.dp, lobes = 12)
                Column(Modifier.padding(start = 16.dp)) {
                    Text("MangaScroll", style = MaterialTheme.typography.headlineMedium)
                    Text("A reader for long-strip manga and webtoons", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { uri.openUri("https://github.com/iDaMelon/manga-scroller") }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Code, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Source code", style = MaterialTheme.typography.titleMedium)
                    Text("github.com/iDaMelon/manga-scroller", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SettingGroup("Open-source licenses") {
            listOf(
                "Space Grotesk font" to "SIL Open Font License 1.1",
                "Outfit font" to "SIL Open Font License 1.1",
                "MaterialKolor" to "MIT License",
                "Jetpack Compose & AndroidX" to "Apache License 2.0",
            ).forEach { (name, lic) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(lic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

fun formatSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 10L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    else -> "%.0f KB".format(b / 1024.0)
}
