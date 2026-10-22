package vn.edu.usth.instaclone.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import vn.edu.usth.instaclone.R
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.ui.home.observeOnline

/**
 * S-06 Profile (T-23, FR-14, UC-10). Draws the state; navigation is passed in.
 * Back arrow is shown when opened from a username/avatar; the Profile tab has none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onLogOut: () -> Unit = {},
    onPostClick: (String) -> Unit = {},
    onMessage: (String) -> Unit = {},
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory(userId)),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isOnline by remember { observeOnline(context) }.collectAsState(initial = true)
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.retry()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.followError) {
        state.followError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFollowError()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = state.user?.username ?: stringResource(R.string.profile_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.profile_back),
                        )
                    }
                }
            },
        )

        if (!isOnline) {
            Text(
                text = stringResource(R.string.profile_offline_banner),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.DarkGray)
                    .padding(8.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> CenteredBox { CircularProgressIndicator() }
                state.notFound -> CenteredBox {
                    Text(
                        text = stringResource(R.string.profile_user_not_found),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                state.errorMessage != null -> CenteredBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(
                            onClick = viewModel::retry,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text(stringResource(R.string.profile_retry))
                        }
                    }
                }
                else -> state.user?.let { user ->
                    ProfileContent(
                        user = user,
                        posts = state.posts,
                        followerCount = state.followerCount,
                        isOwnProfile = state.isOwnProfile,
                        isFollowing = state.isFollowing,
                        isFollowPending = state.isFollowPending,
                        onFollowClick = viewModel::toggleFollow,
                        onEditProfile = onEditProfile,
                        onLogOut = onLogOut,
                        onPostClick = onPostClick,
                        onMessage = { onMessage(userId) },
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ProfileContent(
    user: User,
    posts: List<Post>,
    followerCount: Long,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    isFollowPending: Boolean,
    onFollowClick: () -> Unit,
    onEditProfile: () -> Unit,
    onLogOut: () -> Unit,
    onPostClick: (String) -> Unit,
    onMessage: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileHeader(
                user = user,
                followerCount = followerCount,
                isOwnProfile = isOwnProfile,
                isFollowing = isFollowing,
                isFollowPending = isFollowPending,
                onFollowClick = onFollowClick,
                onEditProfile = onEditProfile,
                onLogOut = onLogOut,
                onMessage = onMessage,
            )
        }

        if (posts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.profile_no_posts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        } else {
            items(posts, key = { it.id }) { post ->
                PostTile(post = post, onClick = { onPostClick(post.id) })
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    user: User,
    followerCount: Long,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    isFollowPending: Boolean,
    onFollowClick: () -> Unit,
    onEditProfile: () -> Unit,
    onLogOut: () -> Unit,
    onMessage: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = stringResource(R.string.profile_avatar, user.username),
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color.LightGray),
                error = ColorPainter(Color.LightGray),
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray),
            )
            Spacer(modifier = Modifier.width(20.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Stat(value = user.postCount, label = stringResource(R.string.profile_posts))
                Stat(value = followerCount, label = stringResource(R.string.profile_followers))
                Stat(value = user.followingCount, label = stringResource(R.string.profile_following))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = user.displayName.ifBlank { user.username },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (user.bio.isNotBlank()) {
            Text(
                text = user.bio,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isOwnProfile) {
                Button(onClick = onEditProfile, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_edit))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.profile_more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_log_out)) },
                            onClick = {
                                menuExpanded = false
                                onLogOut()
                            },
                        )
                    }
                }
            } else {
                Button(
                    onClick = onFollowClick,
                    enabled = !isFollowPending,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isFollowPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                        }
                        Text(
                            stringResource(
                                if (isFollowing) {
                                    R.string.profile_following_button
                                } else {
                                    R.string.profile_follow
                                }
                            )
                        )
                    }
                }
                OutlinedButton(onClick = onMessage, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_message))
                }
            }
        }
    }
}

@Composable
private fun Stat(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PostTile(post: Post, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = post.imageUrl,
            contentDescription = stringResource(R.string.profile_post_by, post.authorUsername),
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color.LightGray),
            error = ColorPainter(Color.LightGray),
            modifier = Modifier.fillMaxSize().background(Color.LightGray),
        )
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        content()
    }
}
