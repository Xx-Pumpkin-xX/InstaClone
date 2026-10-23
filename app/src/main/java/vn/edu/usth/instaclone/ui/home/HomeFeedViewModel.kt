package vn.edu.usth.instaclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.LikeRepository
import vn.edu.usth.instaclone.data.repository.PostDeletionCleanupException
import vn.edu.usth.instaclone.data.repository.PostRepository

/** The All / Following switch above the feed (FR-07, FR-08). */
enum class FeedMode { ALL, FOLLOWING }

class HomeFeedViewModel(
    private val postRepo: PostRepository = AppContainer.postRepository,
    private val likeRepo: LikeRepository = AppContainer.likeRepository,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _mode = MutableStateFlow(FeedMode.ALL)
    val mode: StateFlow<FeedMode> = _mode

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    /** False until the first page of the selected mode has been attempted. */
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded

    /** True when the last first-page attempt failed, so the empty state can offer a retry. */
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed

    // thông báo lỗi (null = không có lỗi)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _cleanupRetryPostId = MutableStateFlow<String?>(null)
    val cleanupRetryPostId: StateFlow<String?> = _cleanupRetryPostId

    // bài nào mình đã tim (lưu id)
    private val _likedPostIds = MutableStateFlow<Set<String>>(emptySet())
    val likedPostIds: StateFlow<Set<String>> = _likedPostIds

    private var endReached = false
    private var refreshJob: Job? = null

    // Bumped whenever the selected mode reloads, so a slow response from the
    // previous mode can never overwrite the current list.
    private var generation = 0

    private suspend fun loadLikedIds(list: List<Post>): Set<String> =
        list.filter { try { likeRepo.isLiked(it.id) } catch (e: Exception) { false } }
            .map { it.id }
            .toSet()

    private var cleanupJob: Job? = null

    private fun retryCleanupInBackground() {
        if (cleanupJob?.isActive == true) return
        cleanupJob = viewModelScope.launch {
            try {
                withTimeout(10.seconds) { postRepo.retryPendingPostCleanups() }
            } catch (e: TimeoutCancellationException) {
                // Markers remain persisted so the next refresh can retry.
                _errorMessage.value = "Couldn't retry pending post cleanup"
            } catch (e: CancellationException) {
                throw e
            } catch (e: PostDeletionCleanupException) {
                _errorMessage.value = e.message
                _cleanupRetryPostId.value = e.postId
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't retry pending post cleanup"
            }
        }
    }

    /** Reloads the selected mode from the newest post (UC-05 1a, 1b). */
    fun refresh() {
        retryCleanupInBackground()
        loadFirstPage(_mode.value)
    }

    /** Pull-to-refresh: same as [refresh], and never shuffles the order. */
    fun pullToRefresh() = refresh()

    /**
     * Switches the All / Following tab. Clears the visible list, liked state and
     * pagination so the two modes can never be mixed, but leaves the post-cleanup
     * retry state untouched.
     */
    fun selectMode(mode: FeedMode) {
        if (_mode.value == mode) return

        _mode.value = mode
        _posts.value = emptyList()
        _likedPostIds.value = emptySet()
        endReached = false
        _loadFailed.value = false
        loadFirstPage(mode)
    }

    private fun loadFirstPage(mode: FeedMode) {
        refreshJob?.cancel()
        val requestGeneration = ++generation

        _isRefreshing.value = true
        _isLoadingMore.value = false
        _hasLoaded.value = false

        refreshJob = viewModelScope.launch {
            try {
                val firstPage = withTimeout(FEED_TIMEOUT_MS) {
                    if (mode == FeedMode.FOLLOWING) {
                        postRepo.followingFeedPage(after = null, limit = PAGE_SIZE)
                    } else {
                        postRepo.feedPage(after = null, limit = PAGE_SIZE)
                    }
                }
                if (requestGeneration != generation) return@launch

                val liked = loadLikedIds(firstPage)
                if (requestGeneration != generation) return@launch

                _posts.value = firstPage
                _likedPostIds.value = liked
                endReached = firstPage.size < PAGE_SIZE
                _loadFailed.value = false
            } catch (e: TimeoutCancellationException) {
                if (requestGeneration == generation) {
                    _loadFailed.value = true
                    _errorMessage.value = LOAD_FAILED_MESSAGE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration == generation) {
                    _loadFailed.value = true
                    _errorMessage.value = messageFor(e, LOAD_FAILED_MESSAGE)
                }
            } finally {
                if (requestGeneration == generation) {
                    _isRefreshing.value = false
                    _hasLoaded.value = true
                }
            }
        }
    }

    // T-14: Delete own post
    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                postRepo.deletePost(postId)
                _posts.value = _posts.value.filter { it.id != postId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PostDeletionCleanupException) {
                _posts.value = _posts.value.filter { it.id != postId }
                _errorMessage.value = e.message
                _cleanupRetryPostId.value = e.postId
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't delete the post"
            }
        }
    }

    fun retryPostCleanup(postId: String) {
        clearError()
        deletePost(postId)
    }

    fun clearError() {
        _errorMessage.value = null
        _cleanupRetryPostId.value = null
    }

    fun loadMore() {
        val mode = _mode.value
        val cursor = _posts.value.lastOrNull() ?: return
        if (_isRefreshing.value || _isLoadingMore.value || endReached || !_hasLoaded.value) return

        val requestGeneration = generation
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val nextPage = withTimeout(FEED_TIMEOUT_MS) {
                    if (mode == FeedMode.FOLLOWING) {
                        postRepo.followingFeedPage(after = cursor, limit = PAGE_SIZE)
                    } else {
                        postRepo.feedPage(after = cursor, limit = PAGE_SIZE)
                    }
                }
                if (requestGeneration != generation) return@launch

                val knownIds = _posts.value.mapTo(mutableSetOf()) { it.id }
                val newPosts = nextPage.filter { knownIds.add(it.id) }
                if (newPosts.isEmpty()) {
                    endReached = true
                    return@launch
                }

                val liked = loadLikedIds(newPosts)
                if (requestGeneration != generation) return@launch

                _posts.value = _posts.value + newPosts
                _likedPostIds.value = _likedPostIds.value + liked
                endReached = nextPage.size < PAGE_SIZE
            } catch (e: TimeoutCancellationException) {
                if (requestGeneration == generation) {
                    _errorMessage.value = LOAD_MORE_FAILED_MESSAGE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration == generation) {
                    _errorMessage.value = messageFor(e, LOAD_MORE_FAILED_MESSAGE)
                }
            } finally {
                if (requestGeneration == generation) {
                    _isLoadingMore.value = false
                }
            }
        }
    }

    // Cộng hoặc trừ số like của 1 bài (them = 1 là tăng, them = -1 là giảm)
    private fun changeLike(postId: String, them: Long) {
        val newList = mutableListOf<Post>()
        for (post in _posts.value) {
            if (post.id == postId) {

                newList.add(post.copy(likeCount = post.likeCount + them))
            } else {
                newList.add(post)
            }
        }
        _posts.value = newList
    }

    private val pendingLikes = mutableSetOf<String>()

    fun toggleLike(postId: String) {
        // Đang chờ kết quả của bài này thì bỏ qua cú chạm (US-08)
        if (!pendingLikes.add(postId)) return

        val nowLiked = postId !in _likedPostIds.value

        // Bước 1: đổi tim và số NGAY trên màn hình
        _likedPostIds.value =
            if (nowLiked) _likedPostIds.value + postId else _likedPostIds.value - postId
        changeLike(postId, if (nowLiked) 1 else -1)

        // Bước 2: báo cho server
        viewModelScope.launch {
            try {
                likeRepo.setLiked(postId, nowLiked)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Bước 3: lỗi thì trả về như cũ
                _likedPostIds.value =
                    if (nowLiked) _likedPostIds.value - postId else _likedPostIds.value + postId
                changeLike(postId, if (nowLiked) -1 else 1)
                _errorMessage.value = "Couldn't update like"
            } finally {
                pendingLikes.remove(postId)
            }
        }
    }
    fun syncCounts() {
        viewModelScope.launch {
            try {
                _posts.value = _posts.value.map { p ->
                    val fresh = postRepo.getPost(p.id)
                    if (fresh != null) p.copy(commentCount = fresh.commentCount) else p
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ignore, next resume will retry
            }
        }
    }

    private fun messageFor(e: Exception, fallback: String): String = when {
        e is FirebaseNetworkException -> OFFLINE_MESSAGE
        e is FirebaseFirestoreException &&
            e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> OFFLINE_MESSAGE
        e is IOException -> OFFLINE_MESSAGE
        else -> fallback
    }

    companion object {
        const val OFFLINE_MESSAGE = "No connection. Check your network and try again."
        const val LOAD_FAILED_MESSAGE = "Couldn't load feed"
        const val LOAD_MORE_FAILED_MESSAGE = "Couldn't load more posts"
        private const val PAGE_SIZE = 10
        private const val FEED_TIMEOUT_MS = 10_000L
    }
}
