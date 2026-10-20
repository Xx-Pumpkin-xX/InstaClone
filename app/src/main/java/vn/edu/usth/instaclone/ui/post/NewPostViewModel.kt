package vn.edu.usth.instaclone.ui.post

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.edu.usth.instaclone.data.repository.FakeImageStorage
import vn.edu.usth.instaclone.data.repository.FakePostRepository
import vn.edu.usth.instaclone.data.repository.ImageStorage
import vn.edu.usth.instaclone.data.repository.PostRepository
import vn.edu.usth.instaclone.util.ImageCompressor
import java.util.UUID

sealed interface NewPostUiState {
    object Idle : NewPostUiState
    data class Compressing(val message: String = "Compressing photo...") : NewPostUiState
    data class Uploading(val message: String = "Uploading photo...") : NewPostUiState
    data class Saving(val message: String = "Saving post...") : NewPostUiState
    object Success : NewPostUiState
    data class Error(val message: String) : NewPostUiState
}

class NewPostViewModel(
    private val imageStorage: ImageStorage,
    private val postRepository: PostRepository
) : ViewModel() {

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption.asStateFlow()

    private val _uiState = MutableStateFlow<NewPostUiState>(NewPostUiState.Idle)
    val uiState: StateFlow<NewPostUiState> = _uiState.asStateFlow()

    private val _showDiscardDialog = MutableStateFlow(false)
    val showDiscardDialog: StateFlow<Boolean> = _showDiscardDialog.asStateFlow()

    fun onImageSelected(uri: Uri?) {
        _selectedUri.value = uri
        _uiState.value = NewPostUiState.Idle
    }

    fun onCaptionChanged(text: String) {
        _caption.value = text
    }

    fun share(context: Context) {
        val uri = _selectedUri.value ?: return
        if (_caption.value.length > 2200) return

        viewModelScope.launch {
            val uid = Firebase.auth.currentUser?.uid ?: "user-nam"
            val postId = UUID.randomUUID().toString()
            val storagePath = "images/posts/$uid/$postId.jpg"

            // Step 1: Compress photo (resize to <=1080px, strip EXIF metadata, max 1MB)
            _uiState.value = NewPostUiState.Compressing()
            val compressedBytes = withContext(Dispatchers.IO) {
                ImageCompressor.compressUri(context, uri)
            }

            if (compressedBytes == null) {
                _uiState.value = NewPostUiState.Error("Couldn't upload the photo. Retry")
                return@launch
            }

            // Step 2: Upload photo to Firebase Storage
            _uiState.value = NewPostUiState.Uploading()
            val imageUrl = try {
                withContext(Dispatchers.IO) {
                    imageStorage.upload(storagePath, compressedBytes)
                }
            } catch (e: Exception) {
                _uiState.value = NewPostUiState.Error("Couldn't upload the photo. Retry")
                return@launch
            }

            // Step 3: Save post to Firestore
            _uiState.value = NewPostUiState.Saving()
            try {
                withContext(Dispatchers.IO) {
                    postRepository.createPost(
                        imageUrl = imageUrl,
                        imagePath = storagePath,
                        caption = _caption.value.trim()
                    )
                }
                _uiState.value = NewPostUiState.Success
            } catch (e: Exception) {
                // If saving fails, attempt to delete uploaded photo cleanup
                withContext(Dispatchers.IO) {
                    try { imageStorage.delete(storagePath) } catch (_: Exception) {}
                }
                _uiState.value = NewPostUiState.Error("Couldn't save the post. Retry")
            }
        }
    }

    fun onBackPress(onNavigateBack: () -> Unit) {
        if (_selectedUri.value != null || _caption.value.isNotBlank()) {
            _showDiscardDialog.value = true
        } else {
            onNavigateBack()
        }
    }

    fun dismissDiscardDialog() {
        _showDiscardDialog.value = false
    }

    fun confirmDiscard(onNavigateBack: () -> Unit) {
        _selectedUri.value = null
        _caption.value = ""
        _showDiscardDialog.value = false
        _uiState.value = NewPostUiState.Idle
        onNavigateBack()
    }

    fun clearError() {
        _uiState.value = NewPostUiState.Idle
    }

    class Factory(
        private val imageStorage: ImageStorage = FakeImageStorage(),
        private val postRepository: PostRepository = FakePostRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NewPostViewModel(imageStorage, postRepository) as T
        }
    }
}
