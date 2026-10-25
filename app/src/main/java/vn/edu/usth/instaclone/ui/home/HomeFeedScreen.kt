package vn.edu.usth.instaclone.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import vn.edu.usth.instaclone.R
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Post

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    onPostClick: (String) -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    viewModel: HomeFeedViewModel = viewModel(),
) {
    val posts by viewModel.posts.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasLoaded by viewModel.hasLoaded.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val likedPostIds by viewModel.likedPostIds.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val cleanupRetryPostId by viewModel.cleanupRetryPostId.collectAsState()
    val context = LocalContext.current
    val retryCleanupLabel = stringResource(R.string.home_retry_cleanup)
    val isOnline by remember { observeOnline(context) }.collectAsState(initial = true)
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentUserId by AppContainer.authRepository.currentUserId.collectAsState()

    // Auto-refresh the selected mode whenever this screen becomes visible
    // (e.g. after uploading a post, following someone, or returning from a detail).
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Each mode starts at the top of its own list (UC-05 1b).
    LaunchedEffect(mode) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
    }

    // Show error snackbar if any operation fails
    LaunchedEffect(errorMessage, cleanupRetryPostId) {
        errorMessage?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (cleanupRetryPostId != null) retryCleanupLabel else null,
                withDismissAction = cleanupRetryPostId != null,
            )
            if (result == SnackbarResult.ActionPerformed) {
                cleanupRetryPostId?.let(viewModel::retryPostCleanup)
            } else {
                viewModel.clearError()
            }
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }

    LaunchedEffect(nearEnd, posts.size) {
        if (nearEnd) viewModel.loadMore()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isOnline) {
                Text(
                    text = stringResource(R.string.home_offline),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(8.dp)
                )
            }

            FeedModeTabs(mode = mode, onModeSelected = viewModel::selectMode)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.pullToRefresh() },
                modifier = Modifier.weight(1f)
            ) {
                // Always a LazyColumn, so pull-to-refresh also works while the
                // list is empty (the common Following mode state).
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (posts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                FeedEmptyContent(
                                    mode = mode,
                                    isOnline = isOnline,
                                    hasLoaded = hasLoaded,
                                    loadFailed = loadFailed,
                                    onRetry = viewModel::refresh,
                                )
                            }
                        }
                    } else {
                        items(posts, key = { it.id }) { post ->
                            val isOwnPost = currentUserId != null && post.authorId == currentUserId
                            PostItem(
                                post = post,
                                isOwnPost = isOwnPost,
                                onClick = { onPostClick(post.id) },
                                onDelete = { viewModel.deletePost(post.id) },
                                onAuthorClick = { onAuthorClick(post.authorId) },
                                isLiked = post.id in likedPostIds,
                                onLikeClick = { viewModel.toggleLike(post.id) }
                            )
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** The All / Following switch (FR-08). */
@Composable
fun FeedModeTabs(mode: FeedMode, onModeSelected: (FeedMode) -> Unit) {
    PrimaryTabRow(selectedTabIndex = mode.ordinal) {
        Tab(
            selected = mode == FeedMode.ALL,
            onClick = { onModeSelected(FeedMode.ALL) },
            text = { Text(stringResource(R.string.home_tab_all)) },
        )
        Tab(
            selected = mode == FeedMode.FOLLOWING,
            onClick = { onModeSelected(FeedMode.FOLLOWING) },
            text = { Text(stringResource(R.string.home_tab_following)) },
        )
    }
}

/**
 * Empty / loading content for the feed. Precedence follows FR-08:
 * loading, then offline, then a failed load, then the mode's empty message.
 */
@Composable
fun FeedEmptyContent(
    mode: FeedMode,
    isOnline: Boolean,
    hasLoaded: Boolean,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    if (!hasLoaded) {
        CircularProgressIndicator()
        return
    }

    if (!isOnline) {
        Text(
            text = stringResource(R.string.home_offline_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    if (loadFailed) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.home_load_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.home_try_again))
            }
        }
        return
    }

    Text(
        text = stringResource(
            if (mode == FeedMode.FOLLOWING) {
                R.string.home_empty_following
            } else {
                R.string.home_empty_all
            }
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun PostItem(
    post: Post,
    isOwnPost: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAuthorClick: () -> Unit = {},
    isLiked: Boolean = false,
    onLikeClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Top header: avatar + username + optional 3-dots menu for own posts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAuthorClick() }
            ) {
                if (post.authorAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = post.authorAvatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = post.authorUsername.ifBlank { "user" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // T-14: Show menu option to delete own post
            if (isOwnPost) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete post",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Post Image rendered with Coil AsyncImage; the grey surfaceVariant
        // background doubles as the placeholder when the photo cannot load.
        val postPhotoDescription = stringResource(R.string.home_post_photo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics {
                    if (post.imageUrl.isBlank()) contentDescription = postPhotoDescription
                }
                .clickable { onClick() }
        ) {
            if (post.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = postPhotoDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Like & Comment counts
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onLikeClick) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.home_like),
                    tint = if (isLiked) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
            Text(text = "${post.likeCount}")
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = stringResource(R.string.home_comments, post.commentCount))
        }

        // Caption
        if (post.caption.isNotBlank()) {
            Text(
                text = post.caption,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // T-14: Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete post?") },
            text = { Text("Are you sure you want to delete this post? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
