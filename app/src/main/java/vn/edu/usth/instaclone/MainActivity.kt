package vn.edu.usth.instaclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import vn.edu.usth.instaclone.ui.navigation.AppNavHost
import vn.edu.usth.instaclone.ui.theme.InstaCloneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InstaCloneTheme {
                AppNavHost()
            }
        }
    }
}