package vn.edu.usth.instaclone.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
    val followerCount: Long = 0,
    val isFollowPending: Boolean = false,
    val followError: String? = null,
    val notFound: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * S-06 Profile (T-23, FR-14, UC-10; FR-16 follow/unfollow).
 *
 * Loads the profile header and posts. The Follow button waits for the repository
 * transaction to finish before changing state, so a failed write leaves the button
 * and follower count exactly as they were (UC-12 2a).
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

    /**
     * Follow or unfollow the profile owner. The UI stays unchanged while the write
     * runs and only flips after the transaction succeeds.
     */
    fun toggleFollow() {
        val state = _uiState.value
        if (state.isLoading || state.isFollowPending || state.isOwnProfile || state.user == null) {
            return
        }
        if (currentUserId() == null) return

        val wasFollowing = state.isFollowing
        _uiState.update { it.copy(isFollowPending = true, followError = null) }

        workScope.launch {
            try {
                // A Firestore transaction retries while offline; fail fast so the button
                // does not stay pending forever (NFR-09).
                withTimeout(FOLLOW_TIMEOUT_MS) {
                    if (wasFollowing) {
                        followRepository.unfollow(userId)
                    } else {
                        followRepository.follow(userId)
                    }
                }
                val followerCount = if (wasFollowing) {
                    (state.followerCount - 1).coerceAtLeast(0)
                } else {
                    state.followerCount + 1
                }
                _uiState.update {
                    it.copy(
                        isFollowing = !wasFollowing,
                        followerCount = followerCount,
                        user = it.user?.copy(followerCount = followerCount),
                        isFollowPending = false,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(isFollowPending = false, followError = OFFLINE_MESSAGE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isFollowPending = false, followError = followMessageFor(e))
                }
            }
        }
    }

    fun clearFollowError() {
        _uiState.update { it.copy(followError = null) }
    }

    private fun load() {
        // The screen retries on every ON_RESUME; never clobber an in-flight follow.
        if (_uiState.value.isFollowPending) return

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
                            isFollowing = false,
                            followerCount = 0,
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
                        followerCount = user.followerCount,
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

    private fun followMessageFor(e: Exception): String = when {
        e is FirebaseNetworkException -> OFFLINE_MESSAGE
        e is FirebaseFirestoreException &&
            e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> OFFLINE_MESSAGE
        e is IOException -> OFFLINE_MESSAGE
        else -> FOLLOW_FAILED_MESSAGE
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
        const val FOLLOW_FAILED_MESSAGE = "Couldn't update follow"
        private const val FOLLOW_TIMEOUT_MS = 10_000L
    }
}
