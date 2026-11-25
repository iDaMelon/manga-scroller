package com.mangascroll

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mangascroll.data.Library
import com.mangascroll.data.sourceFor
import com.mangascroll.ui.AppViewModel
import com.mangascroll.ui.HomeScreen
import com.mangascroll.ui.ImportDialogs
import com.mangascroll.ui.MangaScreen
import com.mangascroll.ui.MangaTheme
import com.mangascroll.ui.Palette
import com.mangascroll.ui.Pickers
import com.mangascroll.ui.ReaderScreen
import com.mangascroll.ui.Screen
import kotlinx.coroutines.delay
import java.io.File
import java.net.URI

const val APP_VERSION = "1.3"

fun main() {
    // MANGASCROLL_DIR lets you keep the library somewhere else (e.g. a portable drive)
    val dir = System.getenv("MANGASCROLL_DIR")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }
    runApp(AppViewModel(Library(dir ?: Library.defaultDir())))
}

fun runApp(vm: AppViewModel) {
    val icon = BitmapPainter(useResource("icon.png") { loadImageBitmap(it) })

    application {
        val state = rememberWindowState(size = DpSize(1280.dp, 860.dp), position = WindowPosition(Alignment.Center))
        var windowed by remember { mutableStateOf(WindowPlacement.Floating) }
        LaunchedEffect(vm.fullscreen) {
            if (vm.fullscreen) {
                windowed = state.placement; state.placement = WindowPlacement.Fullscreen
            } else if (state.placement == WindowPlacement.Fullscreen) {
                state.placement = windowed
            }
        }
        LaunchedEffect(vm.stack.last()) { if (vm.stack.last() !is Screen.Reader) vm.fullscreen = false }

        Window(
            onCloseRequest = { vm.flush(); exitApplication() },
            state = state,
            title = "MangaScroll",
            icon = icon,
            onPreviewKeyEvent = { e ->
                val back = e.type == KeyEventType.KeyDown && vm.stack.size > 1 && vm.pending == null &&
                    ((e.key == Key.Escape && vm.stack.last() !is Screen.Reader) || (e.isAltPressed && e.key == Key.DirectionLeft))
                if (back) vm.back()
                back
            },
        ) {
            window.minimumSize = java.awt.Dimension(720, 520)
            Pickers.owner = window
            MangaTheme(vm.settings) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DropZone(vm) { App(vm) }
                }
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel) {
    when (val s = vm.stack.last()) {
        Screen.Home -> HomeScreen(vm)
        is Screen.Detail -> MangaScreen(vm, s.mangaId)
        is Screen.Reader -> ReaderScreen(vm, s.mangaId, s.chapterId)
        Screen.Settings -> com.mangascroll.ui.SettingsScreen(vm)
        is Screen.SettingsPage -> com.mangascroll.ui.SettingsPageScreen(vm, s.page)
    }
    ImportDialogs(vm)
    vm.toast?.let { msg ->
        LaunchedEffect(msg) { delay(3500); vm.toast = null }
        Box(Modifier.fillMaxSize().padding(bottom = 32.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                msg,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** Drop images, a folder, or ZIP/CBZ files anywhere on the window to import them. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun DropZone(vm: AppViewModel, content: @Composable () -> Unit) {
    var hovering by remember { mutableStateOf(false) }
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hovering = true }
            override fun onExited(event: DragAndDropEvent) { hovering = false }
            override fun onEnded(event: DragAndDropEvent) { hovering = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val data = event.dragData() as? DragData.FilesList ?: return false
                val files = data.readFiles().mapNotNull { runCatching { File(URI(it)) }.getOrNull() }
                val screen = vm.stack.last()
                if (screen is Screen.Reader) return false
                val source = sourceFor(files)
                if (source == null) {
                    vm.toast = "Drop images, one folder, or ZIP/CBZ files"
                    return false
                }
                vm.beginImport(source, (screen as? Screen.Detail)?.mangaId)
                return true
            }
        }
    }
    Box(Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)) {
        content()
        if (hovering && vm.stack.last() !is Screen.Reader) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).padding(24.dp)
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    Text(
                        if (vm.stack.last() is Screen.Detail) "Drop to add chapters" else "Drop to import",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
