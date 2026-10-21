package vn.edu.usth.instaclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.PostRepository

class HomeFeedViewModel(
    private val postRepo: PostRepository = AppContainer.postRepository,
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

    companion object {
        private const val PAGE_SIZE = 10
    }
}
