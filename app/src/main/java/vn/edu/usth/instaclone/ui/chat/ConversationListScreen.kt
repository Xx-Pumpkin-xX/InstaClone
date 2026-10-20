package vn.edu.usth.instaclone.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * S-09 Conversation list (T-29, FR-18). Draws the state; navigation is passed in.
 */
@Composable
fun ConversationListScreen(
    onOpenChat: (otherUserId: String) -> Unit,
    viewModel: ConversationListViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Messages",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            state.isLoading -> CenteredBox { CircularProgressIndicator() }
            state.errorMessage != null -> CenteredBox {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
            state.rows.isEmpty() -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No messages yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Open someone's profile and tap Message.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.rows, key = { it.otherUserId }) { row ->
                    ConversationItem(row, onClick = { onOpenChat(row.otherUserId) })
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(row: ConversationRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialAvatar(row.otherUsername)
        Column(modifier = Modifier.weight(1f)) {
            Text(row.otherUsername, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${row.preview} · ${shortTimeAgo(row.time)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}
