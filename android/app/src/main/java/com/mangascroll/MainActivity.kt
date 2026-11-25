package com.mangascroll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangascroll.ui.AppViewModel
import com.mangascroll.ui.HomeScreen
import com.mangascroll.ui.ImportDialogs
import com.mangascroll.ui.MangaScreen
import com.mangascroll.ui.MangaTheme
import com.mangascroll.ui.ReaderScreen
import com.mangascroll.ui.Screen
import com.mangascroll.ui.SettingsPageScreen
import com.mangascroll.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { App() }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    MangaTheme(vm.settings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BackHandler(enabled = vm.stack.size > 1) { vm.back() }
            when (val s = vm.stack.last()) {
                Screen.Home -> HomeScreen(vm)
                is Screen.Detail -> MangaScreen(vm, s.mangaId)
                is Screen.Reader -> ReaderScreen(vm, s.mangaId, s.chapterId)
                Screen.Settings -> SettingsScreen(vm)
                is Screen.SettingsPage -> SettingsPageScreen(vm, s.page)
            }
            ImportDialogs(vm)
        }
    }
}
