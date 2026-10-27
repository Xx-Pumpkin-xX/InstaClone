package vn.edu.usth.instaclone.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * S-10 Chat (T-30, FR-19). Newest message at the bottom; scroll up to load older ones.
 */
@Composable
fun ChatScreen(
    otherUserId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel { ChatViewModel(otherUserId) },
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    // reverseLayout: index 0 is the newest message, drawn at the bottom.
    val newestFirst = state.bubbles.asReversed()

    // Jump to the newest message whenever a new one arrives (mine or theirs).
    LaunchedEffect(newestFirst.firstOrNull()?.key) {
        if (newestFirst.isNotEmpty()) listState.animateScrollToItem(0)
    }
    // Near the top (oldest loaded message)? Load the previous 30.
    val nearOldest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearOldest) { if (nearOldest) viewModel.loadOlder() }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            InitialAvatar(state.otherUsername, size = 32.dp)
            Text(
                state.otherUsername,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        HorizontalDivider()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.errorMessage != null -> Text(
                    state.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.bubbles.isEmpty() -> Text(
                    "Say hi to ${state.otherUsername} 👋",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(newestFirst, key = { it.key }) { bubble ->
                        MessageBubble(bubble, onRetry = { viewModel.retry(bubble.key) })
                    }
                    if (state.isLoadingOlder) {
                        item(key = "loading-older") {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        if (state.draftLength > MAX_MESSAGE_LENGTH) {
            Text(
                "${state.draftLength}/$MAX_MESSAGE_LENGTH characters",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                placeholder = { Text("Message…") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                isError = state.draftLength > MAX_MESSAGE_LENGTH,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::send, enabled = state.canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(bubble: ChatBubble, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = if (bubble.isMine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (bubble.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (bubble.isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .alpha(if (bubble.status == SendStatus.SENDING) 0.6f else 1f),
        ) {
            Text(bubble.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        if (bubble.status == SendStatus.FAILED) {
            Text(
                "Not sent. Tap to retry",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clickable(onClick = onRetry).padding(top = 2.dp, end = 4.dp),
            )
        }
    }
}
