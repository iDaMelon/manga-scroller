package com.mangascroll

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangascroll.ui.AppViewModel
import com.mangascroll.ui.HomeScreen
import com.mangascroll.ui.ImportDialogs
import com.mangascroll.ui.MangaScreen
import com.mangascroll.ui.ReaderScreen
import com.mangascroll.ui.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { MangaTheme { App() } }
    }
}

@Composable
fun MangaTheme(content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else darkColorScheme(
        primary = Color(0xFFFF8A80),
        secondary = Color(0xFFFFB4A9),
        background = Color(0xFF121218),
        surface = Color(0xFF121218),
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    when (val s = vm.stack.last()) {
        Screen.Home -> HomeScreen(vm)
        is Screen.Detail -> MangaScreen(vm, s.mangaId)
        is Screen.Reader -> ReaderScreen(vm, s.mangaId, s.chapterId)
    }
    ImportDialogs(vm)
}
