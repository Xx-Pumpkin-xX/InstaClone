package vn.edu.usth.instaclone.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp

/** "now", "5m", "3h", "4d", "2w": the short times Instagram shows in the conversation list. */
fun shortTimeAgo(time: Timestamp?): String {
    if (time == null) return "now"
    val minutes = (System.currentTimeMillis() - time.toDate().time) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
        else -> "${minutes / (60 * 24 * 7)}w"
    }
}

/** Round avatar with the first letter of the username (photos come later with the profile work). */
@Composable
fun InitialAvatar(name: String, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = (size.value * 0.4f).sp,
        )
    }
}
