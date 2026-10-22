package vn.edu.usth.instaclone

import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import vn.edu.usth.instaclone.ui.navigation.AppNavHost
import vn.edu.usth.instaclone.ui.theme.InstaCloneTheme
import vn.edu.usth.instaclone.ui.theme.ThemeMode
import vn.edu.usth.instaclone.ui.theme.ThemeSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeSettings.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val mode = ThemeSettings.mode
            val systemDark = isSystemInDarkTheme()
            InstaCloneTheme(
                darkTheme = when (mode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    else -> systemDark
                },
                halloween = mode == ThemeMode.HALLOWEEN,
            ) {
                AppNavHost()
            }
        }
    }
}