package vn.edu.usth.instaclone.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import vn.edu.usth.instaclone.R
import vn.edu.usth.instaclone.data.model.User

/**
 * S-08 Search (FR-17, UC-13, US-13).
 *
 * The screen draws [SearchUiState] and reports a tapped user to the caller so
 * navigation stays in AppNavHost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onUserClick: (String) -> Unit = {},
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val errorMessage = state.errorMessage

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.search_title),
                    fontWeight = FontWeight.Bold,
                )
            },
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.query.isBlank() -> CenteredMessage(stringResource(R.string.search_prompt))
                errorMessage != null -> ErrorContent(
                    message = errorMessage,
                    onRetry = viewModel::retry,
                )
                state.notFound -> CenteredMessage(stringResource(R.string.search_no_users))
                state.results.isEmpty() -> CenteredBox { CircularProgressIndicator() }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.id }) { user ->
                        SearchResultRow(
                            user = user,
                            onClick = { onUserClick(user.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    user: User,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.search_open_profile, user.username),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = stringResource(R.string.search_avatar, user.username),
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color.LightGray),
            error = ColorPainter(Color.LightGray),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.LightGray),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.displayName.ifBlank { user.username },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    CenteredBox {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.search_retry))
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    CenteredBox {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
