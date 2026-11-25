package com.mangascroll.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangascroll.data.Enhanced
import com.mangascroll.reader.Segment
import com.mangascroll.reader.SegmentLoader
import com.mangascroll.reader.buildSegments
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.prefs.Preferences
import kotlin.math.roundToInt

private val BarColor = Color(0xEB16161D)
private val prefs: Preferences = Preferences.userRoot().node("mangascroll")
private const val BASE_WIDTH = 720f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReaderScreen(vm: AppViewModel, mangaId: String, chapterId: String) {
    val all by vm.manga.collectAsState()
    val manga = all.firstOrNull { it.id == mangaId }
    val chapter = manga?.chapters?.firstOrNull { it.id == chapterId }
    if (manga == null || chapter == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val chIndex = manga.chapters.indexOfFirst { it.id == chapterId }
    val prev = manga.chapters.getOrNull(chIndex - 1)
    val next = manga.chapters.getOrNull(chIndex + 1)

    // AI-enhanced pages are used when this chapter has been enhanced and the AI switch is on
    val aiVersion by vm.enhancer.version.collectAsState()
    val aiStatus by vm.enhancer.status.collectAsState()
    val aiReady = remember(chapterId, aiVersion) { Enhanced.isDone(vm.library, mangaId, chapterId) }
    val useAi = aiReady && vm.showEnhanced
    LaunchedEffect(chapterId) { vm.onChapterOpened(mangaId, chapterId) }

    val (segments, pageStarts) = remember(chapterId, useAi) {
        buildSegments(chapter.pages.mapIndexed { i, p ->
            if (useAi) {
                val (w, h) = Enhanced.dims(p.w, p.h)
                Triple(Enhanced.pageFile(vm.library, mangaId, chapterId, i), w, h)
            } else Triple(vm.library.pageFile(mangaId, chapterId, p), p.w, p.h)
        })
    }
    val loader = remember(segments) { SegmentLoader(segments) }
    DisposableEffect(loader) { onDispose { loader.close() } }

    val listState = remember(chapterId) {
        val startAtTop = chapter.read && chapter.posIndex >= segments.size - 1
        if (startAtTop || chapter.posIndex >= segments.size) LazyListState(0, 0)
        else LazyListState(chapter.posIndex, chapter.posOffset)
    }
    val scope = rememberCoroutineScope()

    var zoom by remember { mutableFloatStateOf(prefs.getFloat("zoom", 1f)) }
    fun setZoom(z: Float) {
        zoom = (z * 20).roundToInt() / 20f // steps of 5%
        zoom = zoom.coerceIn(0.5f, 3f)
        prefs.putFloat("zoom", zoom)
    }

    // controls appear when the mouse moves and fade out after a moment
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showBars by remember { mutableStateOf(true) }
    var pinned by remember { mutableStateOf(false) }
    LaunchedEffect(lastMove, pinned) {
        showBars = true
        if (!pinned) {
            delay(2500); showBars = false
        }
    }

    val currentPage by remember(chapterId) {
        derivedStateOf { segments.getOrNull(listState.firstVisibleItemIndex)?.pageIndex ?: (chapter.pages.size - 1) }
    }
    val atEnd by remember(chapterId) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index >= segments.size } }
    }

    // ---- save reading position ----
    val currentAtEnd by rememberUpdatedState(atEnd)
    fun save() {
        val idx = listState.firstVisibleItemIndex.coerceAtMost((segments.size - 1).coerceAtLeast(0))
        val progress = if (currentAtEnd) 1f else (idx + 1f) / segments.size.coerceAtLeast(1)
        vm.saveProgress(mangaId, chapterId, idx, listState.firstVisibleItemScrollOffset, progress, currentAtEnd)
    }
    LaunchedEffect(chapterId) {
        save()
        snapshotFlow { listState.firstVisibleItemIndex to atEnd }.collectLatest { (_, end) ->
            if (!end) delay(700)
            save()
        }
    }
    DisposableEffect(chapterId) { onDispose { save() } }

    val focus = remember { FocusRequester() }
    LaunchedEffect(chapterId) { focus.requestFocus() }

    fun goTo(id: String) = vm.replaceTop(Screen.Reader(mangaId, id))

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF09090C))
            .onPointerEvent(PointerEventType.Move, PointerEventPass.Initial) { lastMove = System.currentTimeMillis() }
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { e ->
                if (e.keyboardModifiers.isCtrlPressed) {
                    val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    setZoom(zoom * if (dy < 0) 1.1f else 1f / 1.1f)
                    e.changes.forEach { it.consume() }
                }
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val page = listState.layoutInfo.viewportSize.height * 0.85f
                when {
                    e.key == Key.Spacebar && e.isShiftPressed -> { scope.launch { listState.animateScrollBy(-page) }; true }
                    e.key == Key.Spacebar || e.key == Key.PageDown -> { scope.launch { listState.animateScrollBy(page) }; true }
                    e.key == Key.PageUp -> { scope.launch { listState.animateScrollBy(-page) }; true }
                    e.key == Key.DirectionDown || e.key == Key.S -> { scope.launch { listState.animateScrollBy(page / 4) }; true }
                    e.key == Key.DirectionUp || e.key == Key.W -> { scope.launch { listState.animateScrollBy(-page / 4) }; true }
                    e.key == Key.MoveHome -> { scope.launch { listState.scrollToItem(0) }; true }
                    e.key == Key.MoveEnd -> { scope.launch { listState.scrollToItem(segments.size) }; true }
                    e.key == Key.DirectionRight || e.key == Key.N -> { next?.let { goTo(it.id) }; true }
                    e.key == Key.DirectionLeft || e.key == Key.P -> { prev?.let { goTo(it.id) }; true }
                    e.key == Key.Equals || e.key == Key.Plus || e.key == Key.NumPadAdd -> { setZoom(zoom + 0.1f); true }
                    e.key == Key.Minus || e.key == Key.NumPadSubtract -> { setZoom(zoom - 0.1f); true }
                    e.key == Key.Zero && e.isCtrlPressed || e.key == Key.Zero -> { setZoom(1f); true }
                    e.key == Key.F11 || e.key == Key.F -> { vm.fullscreen = !vm.fullscreen; true }
                    e.key == Key.A -> { if (aiReady) vm.toggleEnhanced(); true }
                    e.key == Key.Escape -> { if (vm.fullscreen) vm.fullscreen = false else vm.back(); true }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        val density = LocalDensity.current
        val columnWidth = minOf(maxWidth.value, BASE_WIDTH * zoom).dp
        val targetWidth = with(density) { columnWidth.toPx() }.roundToInt()

        LaunchedEffect(chapterId, targetWidth) {
            snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { first ->
                for (i in first until minOf(segments.size, first + 6)) loader.load(segments[i], targetWidth)
            }
        }

        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    pinned = !pinned; lastMove = System.currentTimeMillis()
                },
        ) {
            itemsIndexed(segments, key = { _, s -> s.key }) { _, seg ->
                SegmentImage(seg, loader, targetWidth, Modifier.width(columnWidth))
            }
            item(key = "end") {
                ChapterEnd(
                    chapterName = chapter.name,
                    nextName = next?.name,
                    onNext = { next?.let { goTo(it.id) } },
                    onBack = { vm.back() },
                )
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp),
        )

        // ---- top bar ----
        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(manga.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chapter.name, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                IconButton(onClick = { vm.fullscreen = !vm.fullscreen }) {
                    Icon(if (vm.fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Full screen (F11)", tint = Color.White)
                }
            }
        }

        // ---- bottom bar ----
        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier.padding(16.dp).widthIn(max = 720.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)).background(BarColor)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = prev != null, onClick = { prev?.let { goTo(it.id) } }) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous chapter", tint = if (prev != null) Color.White else Color.Gray)
                    }
                    val pages = chapter.pages.size
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    Slider(
                        value = dragging ?: currentPage.toFloat(),
                        onValueChange = { v ->
                            dragging = v
                            scope.launch { listState.scrollToItem(pageStarts[v.roundToInt().coerceIn(0, pages - 1)]) }
                        },
                        onValueChangeFinished = { dragging = null },
                        valueRange = 0f..(pages - 1).coerceAtLeast(1).toFloat(),
                        enabled = pages > 1,
                        colors = SliderDefaults.colors(
                            thumbColor = Palette.Coral, activeTrackColor = Palette.Coral,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(enabled = next != null, onClick = { next?.let { goTo(it.id) } }) {
                        Icon(Icons.Rounded.SkipNext, "Next chapter", tint = if (next != null) Color.White else Color.Gray)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Page ${currentPage + 1} / ${chapter.pages.size}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (vm.enhancer.available) {
                        val busy = aiStatus?.takeIf { it.mangaId == mangaId && it.chapterId == chapterId }
                        val label = when {
                            busy != null -> "AI ${if (busy.total > 0) busy.done * 100 / busy.total else 0}%"
                            aiReady -> if (vm.showEnhanced) "AI on" else "AI off"
                            manga.enhance != "off" -> "AI queued"
                            else -> "AI off"
                        }
                        val on = aiReady && vm.showEnhanced
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) Color.White else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50))
                                .background(if (on) Palette.SunsetH else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))))
                                .clickable(enabled = aiReady) { vm.toggleEnhanced() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    IconButton(onClick = { setZoom(zoom - 0.1f) }) { Icon(Icons.Rounded.Remove, "Zoom out", tint = Color.White) }
                    Text(
                        "${(zoom * 100).roundToInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { setZoom(1f) }.padding(horizontal = 4.dp),
                    )
                    IconButton(onClick = { setZoom(zoom + 0.1f) }) { Icon(Icons.Rounded.Add, "Zoom in", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun SegmentImage(seg: Segment, loader: SegmentLoader, targetWidth: Int, modifier: Modifier) {
    var bitmap by remember(seg.key) { mutableStateOf<ImageBitmap?>(loader.cached(seg, targetWidth) ?: loader.anyCached(seg)) }
    LaunchedEffect(seg.key, targetWidth) {
        loader.load(seg, targetWidth)?.let { bitmap = it }
    }
    Box(modifier.aspectRatio(seg.ratio)) {
        val b = bitmap
        if (b != null) {
            Image(b, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else if (seg.top == 0) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ChapterEnd(chapterName: String, nextName: String?, onNext: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("End of $chapterName", color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (nextName != null) {
            GradientButton(onClick = onNext) { Text("Next: $nextName  (→)", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            Text("You're all caught up", color = Color.White.copy(alpha = 0.7f))
        }
        OutlinedButton(onClick = onBack) { Text("Back to chapters", color = Color.White) }
        Spacer(Modifier.height(80.dp))
    }
}
