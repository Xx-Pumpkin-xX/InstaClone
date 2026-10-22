package vn.edu.usth.instaclone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import vn.edu.usth.instaclone.R

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

val Creepster = FontFamily(Font(R.font.creepster))
val HalloweenTypography = Typography.copy(
    headlineLarge = Typography.headlineLarge.copy(fontFamily = Creepster),
    headlineMedium = Typography.headlineMedium.copy(fontFamily = Creepster),
    headlineSmall = Typography.headlineSmall.copy(fontFamily = Creepster),
    titleLarge = Typography.titleLarge.copy(fontFamily = Creepster),
)