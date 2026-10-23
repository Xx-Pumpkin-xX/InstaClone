package vn.edu.usth.instaclone.ui.post

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi          // MỚI
import androidx.compose.foundation.combinedClickable                  // MỚI
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
import androidx.compose.material3.AlertDialog                         // MỚI
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton                         // MỚI
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
import vn.edu.usth.instaclone.data.AppContainer                       // MỚI
import vn.edu.usth.instaclone.data.model.Comment
import vn.edu.usth.instaclone.data.model.Post

// SCREEN 1: connected to the ViewModel, loads data and handles errors
@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit = {},
    viewModel: PostDetailViewModel = viewModel(),
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    // MỚI: id người đang đăng nhập, để biết bình luận nào là của mình
    val currentUserId by AppContainer.authRepository.currentUserId.collectAsState()
    val context = LocalContext.current

    // Load the post when the screen opens
    LaunchedEffect(postId) { viewModel.load(postId) }

    // Show a Toast on error, then clear it
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    PostDetailContent(
        post = post,
        comments = comments,
        hasMore = hasMore,
        onLoadMore = { viewModel.loadMoreComments() },
        currentUserId = currentUserId,                              // MỚI
        onDeleteComment = { id -> viewModel.deleteComment(id) },    // MỚI
        onSend = { text, onResult -> viewModel.sendComment(text, onResult) },
        onBack = onBack,
    )
}

// SCREEN 2: UI only (the Preview uses this one)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)   // SỬA: thêm Foundation
@Composable
fun PostDetailContent(
    post: Post?,
    comments: List<Comment>,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    currentUserId: String? = null,            // MỚI
    onDeleteComment: (String) -> Unit = {},   // MỚI
    onSend: (String, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var lastCommentBeforeSend by remember { mutableStateOf<String?>(null) }
    var scrollAfterSend by remember { mutableStateOf(false) }

    LaunchedEffect(scrollAfterSend, comments.lastOrNull()?.id) {
        if (scrollAfterSend && comments.lastOrNull()?.id != lastCommentBeforeSend) {
            val lastIndex = comments.lastIndex + if (post != null) 1 else 0
            if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
            scrollAfterSend = false
        }
    }

    // MỚI: bình luận đang chờ xác nhận xóa (null = không có)
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }

    // Always use the latest values inside LaunchedEffect
    val currentHasMore by rememberUpdatedState(hasMore)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    // Load more only when the user has just scrolled near the end
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

                // Part 1: the post (it can be null)
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

                // Part 2: each comment
                items(comments, key = { it.id }) { comment ->
                    Column(
                        modifier = Modifier
                            .combinedClickable(                       // MỚI: nhấn giữ bình luận
                                onClick = {},
                                onLongClick = {
                                    // chỉ bình luận của mình mới hiện hộp xóa
                                    if (comment.authorId == currentUserId) commentToDelete = comment
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(comment.authorUsername, fontWeight = FontWeight.Bold)
                        Text(comment.text)
                    }
                }
            }

            // Part 3: comment input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
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
                    enabled = isValid,
                ) {
                    Text("Send")
                }
            }
        }
    }

    // MỚI: hộp xác nhận xóa bình luận
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