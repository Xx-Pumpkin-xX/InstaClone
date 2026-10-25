package vn.edu.usth.instaclone.ui.post

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Comment
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.ui.common.timeAgo
import vn.edu.usth.instaclone.ui.home.observeOnline

// SCREEN 1: connected to the ViewModel
@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit = {},
    viewModel: PostDetailViewModel = viewModel(),
) {
    val post by viewModel.post.collectAsState()
    val postMissing by viewModel.postMissing.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentUserId by AppContainer.authRepository.currentUserId.collectAsState()
    val context = LocalContext.current
    val isOnline by remember { observeOnline(context) }.collectAsState(initial = true)

    LaunchedEffect(postId) { viewModel.load(postId) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    PostDetailContent(
        post = post,
        comments = comments,
        postMissing = postMissing,
        isSending = isSending,
        hasMore = hasMore,
        onLoadMore = { viewModel.loadMoreComments() },
        currentUserId = currentUserId,
        onDeleteComment = { id -> viewModel.deleteComment(id) },
        onSend = { text, onResult ->
            if (isOnline) {
                viewModel.sendComment(text, onResult)
            } else {
                Toast.makeText(
                    context,
                    "No connection. Check your network and try again.",
                    Toast.LENGTH_SHORT
                ).show()
                onResult(false)
            }
        },
        onBack = onBack,
    )
}

// SCREEN 2: UI only (the Preview uses this one)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostDetailContent(
    post: Post?,
    comments: List<Comment>,
    postMissing: Boolean = false,
    isSending: Boolean = false,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    currentUserId: String? = null,
    onDeleteComment: (String) -> Unit = {},
    onSend: (String, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // After a successful send, scroll to the newest comment (last in the list)
    var lastCommentBeforeSend by remember { mutableStateOf<String?>(null) }
    var scrollAfterSend by remember { mutableStateOf(false) }
    LaunchedEffect(scrollAfterSend, comments.lastOrNull()?.id) {
        if (scrollAfterSend && comments.lastOrNull()?.id != lastCommentBeforeSend) {
            val lastIndex = comments.lastIndex + if (post != null) 1 else 0
            if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
            scrollAfterSend = false
        }
    }

    var commentToDelete by remember { mutableStateOf<Comment?>(null) }

    val currentHasMore by rememberUpdatedState(hasMore)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            listState.canScrollBackward && last >= listState.layoutInfo.totalItemsCount - 2
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { if (currentHasMore) currentOnLoadMore() }
    }

    val trimmedLength = text.trim().length
    val isValid = trimmedLength in 1..PostDetailViewModel.MAX_COMMENT_LENGTH

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Post") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {

                if (post != null) {
                    item {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(post.authorUsername, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            AsyncImage(
                                model = post.imageUrl,
                                contentDescription = post.caption,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(post.caption)
                        }
                    }
                }

                if (postMissing) {
                    item {
                        Text(
                            "This post is no longer available",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (post != null && comments.isEmpty()) {
                    item {
                        Text(
                            "No comments yet",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                        )
                    }
                }

                items(comments, key = { it.id }) { comment ->
                    Column(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (comment.authorId == currentUserId) commentToDelete = comment
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(comment.authorUsername, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                timeAgo(comment.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(comment.text)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = !isSending && !postMissing,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a comment...") },
                    isError = trimmedLength > PostDetailViewModel.MAX_COMMENT_LENGTH,
                    supportingText = {
                        Text("$trimmedLength/${PostDetailViewModel.MAX_COMMENT_LENGTH}")
                    },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        lastCommentBeforeSend = comments.lastOrNull()?.id
                        onSend(text) { ok ->
                            if (ok) {
                                text = ""
                                scrollAfterSend = true
                            }
                        }
                    },
                    enabled = isValid && !postMissing && !isSending,
                ) {
                    Text("Send")
                }
            }
        }
    }

    commentToDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            title = { Text("Delete comment?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteComment(c.id)
                    commentToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { commentToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PostDetailPreview() {
    PostDetailContent(
        post = Post(id = "1", authorUsername = "demo", caption = "Hello"),
        comments = listOf(
            Comment(id = "c1", authorUsername = "an", text = "Yo"),
            Comment(id = "c2", authorUsername = "binh", text = "SUGOIII"),
        ),
        onSend = { _, _ -> },
    )
}