package vn.edu.usth.instaclone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val HalloweenColorScheme = darkColorScheme(
    primary = HalloweenPumpkin,
    onPrimary = Color(0xFF1A0E00),
    primaryContainer = HalloweenPumpkinDeep,
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = HalloweenWitch,
    onSecondary = HalloweenBone,
    secondaryContainer = HalloweenWitchDeep,
    onSecondaryContainer = Color(0xFFEBDCFF),
    tertiary = HalloweenSlime,
    onTertiary = Color(0xFF0B2A06),
    background = HalloweenNight,
    onBackground = HalloweenBone,
    surface = HalloweenNight,
    onSurface = HalloweenBone,
    surfaceVariant = HalloweenPlumHigh,
    onSurfaceVariant = HalloweenMist,
    surfaceContainer = HalloweenPlum,
    surfaceContainerHigh = HalloweenPlumHigh,
    outline = Color(0xFF6E5A80),
    error = HalloweenBlood,
    onError = Color.White,
    inverseSurface = HalloweenBone,
    inverseOnSurface = HalloweenNight,
)

@Composable
fun InstaCloneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    halloween: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        halloween -> HalloweenColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val useLightIcons = darkTheme || halloween
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !useLightIcons
            controller.isAppearanceLightNavigationBars = !useLightIcons
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (halloween) HalloweenTypography else Typography,
        content = content
    )
}