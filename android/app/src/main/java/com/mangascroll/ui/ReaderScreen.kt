package com.mangascroll.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mangascroll.reader.Segment
import com.mangascroll.reader.SegmentLoader
import com.mangascroll.reader.buildSegments
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val BarColor = Color(0xEB16161D)

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

    // Built once per chapter: pages cut into slices, plus where each page starts.
    val (segments, pageStarts) = remember(chapterId) {
        buildSegments(chapter.pages.map { p -> Triple(vm.library.pageFile(mangaId, chapterId, p), p.w, p.h) })
    }
    val loader = remember(chapterId) { SegmentLoader() }
    DisposableEffect(loader) { onDispose { loader.close() } }

    val listState = remember(chapterId) {
        val startAtTop = chapter.read && chapter.posIndex >= segments.size - 1
        if (startAtTop || chapter.posIndex >= segments.size) LazyListState(0, 0)
        else LazyListState(chapter.posIndex, chapter.posOffset)
    }
    val scope = rememberCoroutineScope()

    var showBars by remember { mutableStateOf(false) }
    var scale by remember(chapterId) { mutableFloatStateOf(1f) }
    var offsetX by remember(chapterId) { mutableFloatStateOf(0f) }
    val animScale by animateFloatAsState(scale, label = "zoom")

    val currentPage by remember(chapterId) {
        derivedStateOf {
            segments.getOrNull(listState.firstVisibleItemIndex)?.pageIndex ?: (chapter.pages.size - 1)
        }
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
            // save right away when the end is reached, otherwise after scrolling settles
            if (!end) delay(700)
            save()
        }
    }
    DisposableEffect(chapterId) { onDispose { save() } }

    // ---- full screen + keep screen on ----
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    LaunchedEffect(showBars) {
        window?.let {
            val c = WindowCompat.getInsetsController(it, view)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showBars) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---- preload the next few slices ----
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val targetWidth = widthPx.roundToInt()
        LaunchedEffect(chapterId, targetWidth) {
            snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { first ->
                for (i in first until minOf(segments.size, first + 5)) loader.load(segments[i], targetWidth)
            }
        }

        val maxOffset = (widthPx * (animScale - 1f)) / 2f
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                // pinch to zoom; when zoomed, one finger pans in any direction
                .pointerInput(chapterId) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                val limit = size.width * (scale - 1f) / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-limit, limit)
                                listState.dispatchRawDelta(-pan.y / scale)
                                event.changes.forEach { it.consume() }
                            } else if (scale > 1.01f && fingers == 1) {
                                val pan = event.calculatePan()
                                val limit = size.width * (scale - 1f) / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-limit, limit)
                                listState.dispatchRawDelta(-pan.y / scale)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (scale < 1.05f) { scale = 1f; offsetX = 0f }
                    }
                }
                .pointerInput(chapterId) {
                    detectTapGestures(
                        onTap = { showBars = !showBars },
                        onDoubleTap = { pos ->
                            if (scale > 1.01f) { scale = 1f; offsetX = 0f }
                            else {
                                scale = 2f
                                offsetX = ((size.width / 2f - pos.x) * 1f).coerceIn(-size.width / 2f, size.width / 2f)
                            }
                        },
                    )
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = animScale
                        scaleY = animScale
                        translationX = offsetX.coerceIn(-maxOffset, maxOffset)
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                itemsIndexed(segments, key = { _, s -> s.key }) { _, seg ->
                    SegmentImage(seg, loader, targetWidth)
                }
                item(key = "end") {
                    ChapterEnd(
                        chapterName = chapter.name,
                        nextName = next?.name,
                        onNext = { next?.let { vm.replaceTop(Screen.Reader(mangaId, it.id)) } },
                        onBack = { vm.back() },
                    )
                }
            }
        }

        // ---- top bar ----
        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars).padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(manga.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chapter.name, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }

        // ---- bottom bar ----
        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)).background(BarColor)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = prev != null, onClick = { prev?.let { vm.replaceTop(Screen.Reader(mangaId, it.id)) } }) {
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
                    IconButton(enabled = next != null, onClick = { next?.let { vm.replaceTop(Screen.Reader(mangaId, it.id)) } }) {
                        Icon(Icons.Rounded.SkipNext, "Next chapter", tint = if (next != null) Color.White else Color.Gray)
                    }
                }
                Text(
                    "Page ${currentPage + 1} / ${chapter.pages.size}   ·   ${if (scale > 1.01f) "${(scale * 100).roundToInt()}% · double-tap to reset" else "pinch or double-tap to zoom"}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun SegmentImage(seg: Segment, loader: SegmentLoader, targetWidth: Int) {
    var bitmap by remember(seg.key) { mutableStateOf<Bitmap?>(loader.cached(seg)) }
    LaunchedEffect(seg.key, targetWidth) {
        if (bitmap == null) bitmap = loader.load(seg, targetWidth)
    }
    Box(Modifier.fillMaxWidth().aspectRatio(seg.ratio)) {
        val b = bitmap
        if (b != null) {
            Image(
                b.asImageBitmap(), null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (seg.top == 0) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ChapterEnd(chapterName: String, nextName: String?, onNext: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp).windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("End of $chapterName", color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (nextName != null) {
            GradientButton(onClick = onNext) { Text("Next: $nextName", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            Text("You're all caught up", color = Color.White.copy(alpha = 0.7f))
        }
        OutlinedButton(onClick = onBack) { Text("Back to chapters", color = Color.White) }
        Spacer(Modifier.height(32.dp))
    }
}
