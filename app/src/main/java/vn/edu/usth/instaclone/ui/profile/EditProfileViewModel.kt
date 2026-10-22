package vn.edu.usth.instaclone.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.ImageStorage
import vn.edu.usth.instaclone.data.repository.UserRepository
import vn.edu.usth.instaclone.util.ImageCompressor
import java.io.IOException

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val selectedAvatarUri: String? = null,
    val hasChanges: Boolean = false,
    val userLoaded: Boolean = false,
    val errorMessage: String? = null,
    val saveComplete: Boolean = false,
)

class EditProfileViewModel(
    private val userRepository: UserRepository = AppContainer.userRepository,
    private val imageStorage: ImageStorage = AppContainer.profileImageStorage,
    private val currentUserId: () -> String? = {
        AppContainer.authRepository.currentUserId.value
    },
    private val compressImage: (Context?, String) -> ByteArray? = { context, uri ->
        context?.let { ImageCompressor.compressUri(it, Uri.parse(uri)) }
    },
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val workScope = scope ?: viewModelScope
    private var originalUser: User? = null

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun onDisplayNameChanged(value: String) {
        _uiState.update {
            val updated = it.copy(displayName = value, errorMessage = null, saveComplete = false)
            updated.copy(hasChanges = hasChanges(updated))
        }
    }

    fun onBioChanged(value: String) {
        _uiState.update {
            val updated = it.copy(bio = value, errorMessage = null, saveComplete = false)
            updated.copy(hasChanges = hasChanges(updated))
        }
    }

    fun onAvatarSelected(uri: String?) {
        _uiState.update {
            val updated = it.copy(selectedAvatarUri = uri, errorMessage = null, saveComplete = false)
            updated.copy(hasChanges = hasChanges(updated))
        }
    }

    fun loadProfile() {
        val uid = currentUserId()
        if (uid == null) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = LOAD_FAILED_MESSAGE)
            }
            return
        }

        workScope.launch {
            _uiState.update { it.copy(isLoading = true, userLoaded = false, errorMessage = null) }
            try {
                val user = userRepository.getUser(uid)
                if (user == null) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = USER_NOT_FOUND_MESSAGE)
                    }
                    return@launch
                }
                originalUser = user
                _uiState.value = EditProfileUiState(
                    isLoading = false,
                    userLoaded = true,
                    username = user.username,
                    displayName = user.displayName,
                    bio = user.bio,
                    avatarUrl = user.avatarUrl,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = messageFor(e))
                }
            }
        }
    }

    fun save(context: Context?) {
        val state = _uiState.value
        val uid = currentUserId()
        if (state.isLoading || state.isSaving || !isValid(state) || !state.hasChanges) return
        if (uid == null) {
            _uiState.update { it.copy(errorMessage = LOAD_FAILED_MESSAGE) }
            return
        }

        workScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveComplete = false) }
            var newAvatarUrl: String? = null
            val selectedAvatar = state.selectedAvatarUri
            if (selectedAvatar != null) {
                val bytes = try {
                    withContext(Dispatchers.IO) { compressImage(context, selectedAvatar) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    finishWithError(messageForUpload(e))
                    return@launch
                }
                if (bytes == null) {
                    finishWithError(UPLOAD_FAILED_MESSAGE)
                    return@launch
                }
                if (bytes.size > ImageCompressor.MAX_FILE_SIZE_BYTES) {
                    finishWithError(UPLOAD_FAILED_MESSAGE)
                    return@launch
                }

                newAvatarUrl = try {
                    withContext(Dispatchers.IO) {
                        imageStorage.upload("images/avatars/$uid.jpg", bytes)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    finishWithError(messageForUpload(e))
                    return@launch
                }
            }

            try {
                withContext(Dispatchers.IO) {
                    userRepository.updateProfile(
                        displayName = state.displayName,
                        bio = state.bio,
                        avatarUrl = newAvatarUrl,
                    )
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        avatarUrl = newAvatarUrl ?: it.avatarUrl,
                        selectedAvatarUri = null,
                        hasChanges = false,
                        saveComplete = true,
                        errorMessage = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finishWithError(messageForSave(e))
            }
        }
    }

    private fun finishWithError(message: String) {
        _uiState.update { it.copy(isSaving = false, errorMessage = message) }
    }

    private fun hasChanges(state: EditProfileUiState): Boolean {
        val original = originalUser ?: return false
        return state.displayName != original.displayName ||
            state.bio != original.bio ||
            state.selectedAvatarUri != null
    }

    private fun isValid(state: EditProfileUiState): Boolean =
        state.displayName.length <= MAX_DISPLAY_NAME_LENGTH &&
            state.bio.length <= MAX_BIO_LENGTH

    private fun messageFor(e: Exception): String =
        if (isOfflineFailure(e)) OFFLINE_MESSAGE else LOAD_FAILED_MESSAGE

    private fun messageForUpload(e: Exception): String =
        if (isOfflineFailure(e)) OFFLINE_MESSAGE else UPLOAD_FAILED_MESSAGE

    private fun messageForSave(e: Exception): String =
        if (isOfflineFailure(e)) OFFLINE_MESSAGE else SAVE_FAILED_MESSAGE

    private fun isOfflineFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any {
            it is FirebaseNetworkException ||
                it is IOException ||
                (it is FirebaseFirestoreException &&
                    it.code == FirebaseFirestoreException.Code.UNAVAILABLE) ||
                (it is StorageException &&
                    it.errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED)
        }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditProfileViewModel() as T
    }

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 30
        const val MAX_BIO_LENGTH = 150
        const val OFFLINE_MESSAGE = "No connection. Check your network and try again."
        const val LOAD_FAILED_MESSAGE = "Couldn't load your profile. Retry"
        const val USER_NOT_FOUND_MESSAGE = "User not found"
        const val UPLOAD_FAILED_MESSAGE = "Couldn't upload the photo. Retry"
        const val SAVE_FAILED_MESSAGE = "Couldn't save your profile. Retry"
    }
}
