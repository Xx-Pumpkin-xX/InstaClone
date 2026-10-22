package vn.edu.usth.instaclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.LikeRepository
import vn.edu.usth.instaclone.data.repository.PostImageCleanupException
import vn.edu.usth.instaclone.data.repository.PostRepository
class HomeFeedViewModel(
    private val postRepo: PostRepository = AppContainer.postRepository,
    private val likeRepo: LikeRepository = AppContainer.likeRepository,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var endReached = false

    // bài nào mình đã tim (lưu id)
    private val _likedPostIds = MutableStateFlow<Set<String>>(emptySet())
    val likedPostIds: StateFlow<Set<String>> = _likedPostIds

    // thông báo lỗi (null = không có lỗi)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val firstPage = postRepo.feedPage(after = null, limit = PAGE_SIZE)
                _posts.value = firstPage
                endReached = firstPage.size < PAGE_SIZE
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't load feed"
            } finally {
                _isRefreshing.value = false
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
            } catch (e: PostImageCleanupException) {
                _posts.value = _posts.value.filter { it.id != postId }
                _errorMessage.value = e.message
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't delete the post"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun loadMore() {
        if (_isRefreshing.value || _isLoadingMore.value || endReached) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val nextPage = postRepo.feedPage(after = _posts.value.lastOrNull(), limit = PAGE_SIZE)
                _posts.value = _posts.value + nextPage
                endReached = nextPage.size < PAGE_SIZE
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't load more posts"
            } finally {
                _isLoadingMore.value = false
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
    companion object {
        private const val PAGE_SIZE = 10
    }
}
