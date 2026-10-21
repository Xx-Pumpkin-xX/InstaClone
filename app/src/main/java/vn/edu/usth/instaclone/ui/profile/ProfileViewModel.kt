package vn.edu.usth.instaclone.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.FollowRepository
import vn.edu.usth.instaclone.data.repository.PostRepository
import vn.edu.usth.instaclone.data.repository.UserRepository
import java.io.IOException

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val isOwnProfile: Boolean = false,
    val isFollowing: Boolean = false,
    val notFound: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * S-06 Profile (T-23, FR-14, UC-10).
 *
 * Loads the profile header and the member's posts. Follow/unfollow itself is FR-16,
 * so this screen only reads [FollowRepository.isFollowing] to label the button.
 */
class ProfileViewModel(
    private val userId: String,
    private val userRepository: UserRepository = AppContainer.userRepository,
    private val postRepository: PostRepository = AppContainer.postRepository,
    private val followRepository: FollowRepository = AppContainer.followRepository,
    private val currentUserId: () -> String? = {
        AppContainer.authRepository.currentUserId.value
    },
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val workScope: CoroutineScope = scope ?: viewModelScope
    private val isOwnProfile = currentUserId()?.let { it == userId } ?: false

    private val _uiState = MutableStateFlow(ProfileUiState(isOwnProfile = isOwnProfile))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        workScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, notFound = false) }
            try {
                val user = userRepository.getUser(userId)
                if (user == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = null,
                            posts = emptyList(),
                            notFound = true,
                        )
                    }
                    return@launch
                }

                val posts = try {
                    postRepository.userPosts(userId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                val isFollowing = if (isOwnProfile) {
                    false
                } else {
                    try {
                        followRepository.isFollowing(userId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        user = user,
                        posts = posts,
                        isFollowing = isFollowing,
                        isOwnProfile = isOwnProfile,
                        notFound = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = messageFor(e)) }
            }
        }
    }

    private fun messageFor(e: Exception): String = when (e) {
        is FirebaseNetworkException -> OFFLINE_MESSAGE
        is IOException -> OFFLINE_MESSAGE
        else -> LOAD_FAILED_MESSAGE
    }

    class Factory(
        private val userId: String,
        private val userRepository: UserRepository = AppContainer.userRepository,
        private val postRepository: PostRepository = AppContainer.postRepository,
        private val followRepository: FollowRepository = AppContainer.followRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(userId, userRepository, postRepository, followRepository) as T
        }
    }

    companion object {
        const val OFFLINE_MESSAGE = "No connection. Check your network and try again."
        const val LOAD_FAILED_MESSAGE = "Couldn't load profile"
    }
}
