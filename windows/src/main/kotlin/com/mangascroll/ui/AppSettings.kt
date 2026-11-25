package com.mangascroll.ui

import androidx.compose.runtime.mutableStateOf
import java.util.prefs.Preferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** User preferences. Each one is saved immediately and is observable by Compose. */
class AppSettings {
    private val prefs: Preferences = Preferences.userRoot().node("mangascroll")

    private inner class Str(val key: String, val def: String) : ReadWriteProperty<Any?, String> {
        val state = mutableStateOf(prefs.get(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value; prefs.put(key, value)
        }
    }

    private inner class Bool(val key: String, val def: Boolean) : ReadWriteProperty<Any?, Boolean> {
        val state = mutableStateOf(prefs.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value; prefs.putBoolean(key, value)
        }
    }

    private inner class IntPref(val key: String, val def: Int) : ReadWriteProperty<Any?, Int> {
        val state = mutableStateOf(prefs.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.value = value; prefs.putInt(key, value)
        }
    }

    // ----- look & feel -----
    /** "system", "light", "dark" or "black" */
    var theme by Str("theme", "system")
    var accent by IntPref("accent", 0)
    /** "space", "outfit" or "system" */
    var font by Str("font", "space")

    // ----- library -----
    var coverSize by Str("coverSize", "medium")
    var defaultSort by Str("defaultSort", "Recent")
    /** AI enhance mode given to newly imported manga: "off", "reading" or "all" */
    var defaultEnhance by Str("defaultEnhance", "off")
    /** after a chapter is enhanced, delete its originals and keep only the AI pages */
    var replaceOriginals by Bool("replaceOriginals", false)

    // ----- reader -----
    var readerBg by Str("readerBg", "black")
    var pageWidth by Str("pageWidth", "fit")
    var pageGaps by Bool("pageGaps", false)
    var autoNext by Bool("autoNext", false)
    var sharper by Bool("sharper", true)
    var autoHide by Bool("autoHide", true)
}
