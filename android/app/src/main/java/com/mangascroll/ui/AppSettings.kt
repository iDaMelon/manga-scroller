package com.mangascroll.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** User preferences. Each one is saved immediately and is observable by Compose. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private inner class Str(val key: String, val def: String) : ReadWriteProperty<Any?, String> {
        val state = mutableStateOf(prefs.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value; prefs.edit()?.putString(key, value)?.apply()
        }
    }

    private inner class Bool(val key: String, val def: Boolean) : ReadWriteProperty<Any?, Boolean> {
        val state = mutableStateOf(prefs.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value; prefs.edit()?.putBoolean(key, value)?.apply()
        }
    }

    private inner class IntPref(val key: String, val def: Int) : ReadWriteProperty<Any?, Int> {
        val state = mutableStateOf(prefs.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.value = value; prefs.edit()?.putInt(key, value)?.apply()
        }
    }

    // ----- look & feel -----
    /** "system", "light", "dark" or "black" */
    var theme by Str("theme", "system")
    /** index into [Accents] */
    var accent by IntPref("accent", 0)
    /** use the wallpaper colors (Android 12+) */
    var materialYou by Bool("materialYou", false)
    /** "space", "outfit" or "system" */
    var font by Str("font", "space")

    // ----- library -----
    /** "small", "medium" or "large" */
    var coverSize by Str("coverSize", "medium")
    /** "Recent", "Title", "Unread" or "Added" */
    var defaultSort by Str("defaultSort", "Recent")

    // ----- reader -----
    /** "black", "gray" or "white" */
    var readerBg by Str("readerBg", "black")
    /** "fit" (fill the width) or "original" (1 image pixel = 1 screen pixel) */
    var pageWidth by Str("pageWidth", "fit")
    var pageGaps by Bool("pageGaps", false)
    var autoNext by Bool("autoNext", false)
    var keepScreenOn by Bool("keepScreenOn", true)
    var sharper by Bool("sharper", true)
}
