package vn.edu.usth.instaclone.ui.post

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.edu.usth.instaclone.data.AppContainer
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
    data class Error(val message: String, val allowEditing: Boolean = true) : NewPostUiState
}

class NewPostViewModel(
    private val imageStorage: ImageStorage,
    private val postRepository: PostRepository
) : ViewModel() {

    private data class PendingUpload(
        val postId: String,
        val uid: String,
        val imageUrl: String
    )

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption.asStateFlow()

    private val _uiState = MutableStateFlow<NewPostUiState>(NewPostUiState.Idle)
    val uiState: StateFlow<NewPostUiState> = _uiState.asStateFlow()

    private val _showDiscardDialog = MutableStateFlow(false)
    val showDiscardDialog: StateFlow<Boolean> = _showDiscardDialog.asStateFlow()

    private var shareJob: Job? = null
    private var pendingPostId: String? = null
    private var pendingUpload: PendingUpload? = null

    fun onImageSelected(uri: Uri?) {
        if (!canEditDraft()) return
        if (uri != _selectedUri.value) {
            pendingPostId = null
            pendingUpload = null
        }
        _selectedUri.value = uri
        _uiState.value = NewPostUiState.Idle
    }

    fun onCaptionChanged(text: String) {
        if (!canEditDraft()) return
        if (text != _caption.value) {
            pendingPostId = null
            pendingUpload = null
        }
        _caption.value = text
    }

    fun share(context: Context) {
        if (shareJob?.isActive == true || isProcessing()) return
        val uri = _selectedUri.value ?: return
        val caption = _caption.value.trim()
        if (caption.length > 2200) return

        val postId = pendingPostId ?: UUID.randomUUID().toString().also {
            pendingPostId = it
        }
        _uiState.value = NewPostUiState.Compressing()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val uid = Firebase.auth.currentUser?.uid
                if (uid == null) {
                    _uiState.value = NewPostUiState.Error("Sign in to share a post")
                    return@launch
                }
                val storagePath = "images/posts/$uid/$postId.jpg"

                val cachedUpload = pendingUpload?.takeIf {
                    it.postId == postId && it.uid == uid
                }
                val uploadedUrl = if (cachedUpload != null) {
                    cachedUpload.imageUrl
                } else {
                    // Step 1: Compress photo (resize to <=1080px, strip EXIF metadata, max 1MB)
                    val compressedBytes = try {
                        withContext(Dispatchers.IO) {
                            ImageCompressor.compressUri(context, uri)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.value = NewPostUiState.Error("Couldn't upload the photo. Retry")
                        return@launch
                    }

                    if (compressedBytes == null ||
                        compressedBytes.size > ImageCompressor.MAX_FILE_SIZE_BYTES
                    ) {
                        _uiState.value = NewPostUiState.Error("Couldn't upload the photo. Retry")
                        return@launch
                    }

                    // Step 2: Upload photo to Firebase Storage
                    _uiState.value = NewPostUiState.Uploading()
                    val url = try {
                        withContext(Dispatchers.IO) {
                            imageStorage.upload(storagePath, compressedBytes)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _uiState.value = NewPostUiState.Error("Couldn't upload the photo. Retry")
                        return@launch
                    }
                    pendingUpload = PendingUpload(postId, uid, url)
                    url
                }

                // Step 3: Save post to Firestore / Repository
                _uiState.value = NewPostUiState.Saving()
                try {
                    withContext(Dispatchers.IO) {
                        postRepository.createPost(
                            imageUrl = uploadedUrl,
                            imagePath = storagePath,
                            caption = caption
                        )
                    }
                    // Reset form and notify success
                    _selectedUri.value = null
                    _caption.value = ""
                    pendingPostId = null
                    pendingUpload = null
                    _uiState.value = NewPostUiState.Success
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _uiState.value = NewPostUiState.Error(
                        message = "Couldn't save the post. Retry",
                        allowEditing = false
                    )
                }
            } finally {
                shareJob = null
            }
        }
        shareJob = job
        job.start()
    }

    private fun isProcessing(): Boolean =
        _uiState.value is NewPostUiState.Compressing ||
            _uiState.value is NewPostUiState.Uploading ||
            _uiState.value is NewPostUiState.Saving

    private fun canEditDraft(): Boolean =
        !isProcessing() &&
            shareJob?.isActive != true &&
            (_uiState.value is NewPostUiState.Idle ||
                (_uiState.value as? NewPostUiState.Error)?.allowEditing == true)

    fun onBackPress(onNavigateBack: () -> Unit) {
        if (!canEditDraft()) return
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
        if (!canEditDraft()) return
        _selectedUri.value = null
        _caption.value = ""
        pendingPostId = null
        pendingUpload = null
        _showDiscardDialog.value = false
        _uiState.value = NewPostUiState.Idle
        onNavigateBack()
    }

    fun clearError() {
        _uiState.value = NewPostUiState.Idle
    }

    class Factory(
        private val imageStorage: ImageStorage = AppContainer.imageStorage,
        private val postRepository: PostRepository = AppContainer.postRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NewPostViewModel(imageStorage, postRepository) as T
        }
    }
}
