package vn.edu.usth.instaclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.FakePostRepository
import vn.edu.usth.instaclone.data.repository.PostRepository

class HomeFeedViewModel(
    private val postRepo: PostRepository = FakePostRepository(),
) : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private var endReached = false

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

    companion object {
        private const val PAGE_SIZE = 10
    }
}