package vn.edu.usth.instaclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.FakePostRepository
import vn.edu.usth.instaclone.data.repository.PostRepository
import vn.edu.usth.instaclone.data.repository.LikeRepository
import vn.edu.usth.instaclone.data.repository.FakeLikeRepository
class HomeFeedViewModel(
    private val postRepo: PostRepository = AppContainer.postRepository,
    private val likeRepo: LikeRepository = FakeLikeRepository(),
) : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private var endReached = false

    // bài nào mình đã tim (lưu id)
    private val _likedPostIds = MutableStateFlow<Set<String>>(emptySet())
    val likedPostIds: StateFlow<Set<String>> = _likedPostIds

    // thông báo lỗi (null = không có lỗi)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        refresh()
    }

    // Tải lại từ đầu
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val firstPage = postRepo.feedPage(after = null, limit = PAGE_SIZE)
            _posts.value = firstPage
            endReached = firstPage.size < PAGE_SIZE
            _isRefreshing.value = false
        }
    }

    // Tải thêm 10 bài, nối vào cuối danh sách
    fun loadMore() {
        if (_isRefreshing.value || _isLoadingMore.value || endReached) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val nextPage = postRepo.feedPage(after = _posts.value.lastOrNull(), limit = PAGE_SIZE)
            _posts.value = _posts.value + nextPage
            endReached = nextPage.size < PAGE_SIZE
            _isLoadingMore.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // Cộng hoặc trừ số like của 1 bài (them = 1 là tăng, them = -1 là giảm)
    private fun doiSoLike(postId: String, them: Long) {
        val danhSachMoi = mutableListOf<Post>()
        for (post in _posts.value) {
            if (post.id == postId) {
                
                danhSachMoi.add(post.copy(likeCount = post.likeCount + them))
            } else {
                danhSachMoi.add(post)
            }
        }
        _posts.value = danhSachMoi
    }

    fun toggleLike(postId: String) {
        val daLike = postId in _likedPostIds.value

        // Bước 1: đổi tim và số NGAY trên màn hình
        if (daLike) {
            _likedPostIds.value = _likedPostIds.value - postId
            doiSoLike(postId, -1)
        } else {
            _likedPostIds.value = _likedPostIds.value + postId
            doiSoLike(postId, 1)
        }

        // Bước 2: báo cho server
        viewModelScope.launch {
            try {
                if (daLike) {
                    postRepo.unlikePost(postId)
                } else {
                    postRepo.likePost(postId)
                }
            } catch (e: Exception) {
                // Bước 3: server lỗi thì trả về như cũ
                if (daLike) {
                    _likedPostIds.value = _likedPostIds.value + postId
                    doiSoLike(postId, 1)
                } else {
                    _likedPostIds.value = _likedPostIds.value - postId
                    doiSoLike(postId, -1)
                }
                _errorMessage.value = "Couldn't update your like. Please try again."
            }
        }
    }
    companion object {
        private const val PAGE_SIZE = 10
    }
}