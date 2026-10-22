package vn.edu.usth.instaclone.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
    HALLOWEEN("Halloween 🎃"),
}

object ThemeSettings {
    private const val PREFS = "theme_settings"
    private const val KEY_MODE = "mode"
    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY_MODE, null)
        mode = ThemeMode.entries.firstOrNull {it.name == saved} ?: ThemeMode.SYSTEM
    }
    fun select(newMode: ThemeMode){
        mode = newMode
        prefs?.edit { putString(KEY_MODE, newMode.name) }
    }
}