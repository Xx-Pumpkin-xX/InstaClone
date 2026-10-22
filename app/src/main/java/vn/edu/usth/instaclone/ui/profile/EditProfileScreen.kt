package vn.edu.usth.instaclone.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import vn.edu.usth.instaclone.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.Factory()),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.onAvatarSelected(uri.toString())
    }

    LaunchedEffect(state.saveComplete) {
        if (state.saveComplete) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.edit_profile_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            !state.userLoaded -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = viewModel::loadProfile, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.profile_retry))
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val avatarModel: Any? = state.selectedAvatarUri?.let(Uri::parse)
                    ?: state.avatarUrl.takeIf { it.isNotBlank() }
                AsyncImage(
                    model = avatarModel,
                    contentDescription = stringResource(R.string.edit_profile_avatar),
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(Color.LightGray),
                    error = ColorPainter(Color.LightGray),
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray),
                )
                Button(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !state.isSaving,
                ) {
                    Text(stringResource(R.string.edit_profile_change_photo))
                }
                OutlinedTextField(
                    value = state.username,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.edit_profile_username)) },
                    supportingText = { Text(stringResource(R.string.edit_profile_username_read_only)) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onDisplayNameChanged,
                    label = { Text(stringResource(R.string.edit_profile_display_name)) },
                    supportingText = {
                        Text(
                            if (state.displayName.length > EditProfileViewModel.MAX_DISPLAY_NAME_LENGTH) {
                                stringResource(R.string.edit_profile_display_name_too_long)
                            } else {
                                "${state.displayName.length}/${EditProfileViewModel.MAX_DISPLAY_NAME_LENGTH}"
                            }
                        )
                    },
                    isError = state.displayName.length > EditProfileViewModel.MAX_DISPLAY_NAME_LENGTH,
                    singleLine = true,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.bio,
                    onValueChange = viewModel::onBioChanged,
                    label = { Text(stringResource(R.string.edit_profile_bio)) },
                    supportingText = {
                        Text(
                            if (state.bio.length > EditProfileViewModel.MAX_BIO_LENGTH) {
                                stringResource(R.string.edit_profile_bio_too_long)
                            } else {
                                "${state.bio.length}/${EditProfileViewModel.MAX_BIO_LENGTH}"
                            }
                        )
                    },
                    isError = state.bio.length > EditProfileViewModel.MAX_BIO_LENGTH,
                    minLines = 3,
                    maxLines = 5,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.save(context) },
                    enabled = !state.isSaving &&
                        state.hasChanges &&
                        state.displayName.length <= EditProfileViewModel.MAX_DISPLAY_NAME_LENGTH &&
                        state.bio.length <= EditProfileViewModel.MAX_BIO_LENGTH,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.edit_profile_save))
                    }
                }
            }
        }
    }
}
