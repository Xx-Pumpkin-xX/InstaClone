package vn.edu.usth.instaclone.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Comment
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.CommentRepository
import vn.edu.usth.instaclone.data.repository.PostRepository

class PostDetailViewModel(
    private val postRepo: PostRepository = AppContainer.postRepository,
    private val commentRepo: CommentRepository = AppContainer.commentRepository,
) : ViewModel() {

    // The post being viewed (null = not loaded yet or not found)
    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post

    // true = loaded, but the post no longer exists
    private val _postMissing = MutableStateFlow(false)
    val postMissing: StateFlow<Boolean> = _postMissing

    // Comments currently shown (oldest first)
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore

    // MỚI: true = a comment is being sent; blocks further sends
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var allComments: List<Comment> = emptyList()
    private var shown = PAGE_SIZE
    private var postId: String = ""

    private fun publish() {
        _comments.value = allComments.take(shown)
        _hasMore.value = allComments.size > shown
    }

    fun loadMoreComments() {
        if (!_hasMore.value) return
        shown += PAGE_SIZE
        publish()
    }

    fun load(id: String) {
        if (id == postId) return        // already loaded successfully
        viewModelScope.launch {
            try {
                val loadedPost = postRepo.getPost(id)
                val loadedComments = commentRepo.comments(id)
                postId = id
                _post.value = loadedPost
                _postMissing.value = loadedPost == null
                allComments = loadedComments
                shown = PAGE_SIZE
                publish()
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't load the post"
            }
        }
    }

    // onResult: true = the comment was saved, false = it was NOT saved
    fun sendComment(text: String, onResult: (Boolean) -> Unit = {}) {
        val content = text.trim()
        if (_isSending.value || content.isEmpty() || content.length > MAX_COMMENT_LENGTH || postId.isEmpty()) {
            onResult(false)
            return
        }
        _isSending.value = true
        viewModelScope.launch {
            try {
                // Step 1: save the comment. Only a failure here means "not posted".
                // withTimeout: if the network is down the save never finishes, so give up
                // after SEND_TIMEOUT_MS instead of leaving the screen stuck in "sending".
                val saved = try {
                    withTimeout(SEND_TIMEOUT_MS) {
                        commentRepo.addComment(postId, content)
                    }
                    true
                } catch (e: Exception) {
                    _errorMessage.value = "Couldn't post your comment. Retry"
                    false
                }
                if (!saved) {
                    onResult(false)
                    return@launch
                }

                // The comment is saved, so report success and clear the input.
                onResult(true)

                // Step 2: reload the list. A failure here must NOT ask the user to resend.
                try {
                    allComments = commentRepo.comments(postId)
                    // New comment is last (oldest first); reveal it even if older pages are hidden
                    shown = allComments.size
                    publish()
                } catch (e: Exception) {
                    _errorMessage.value = "Comment posted, but couldn't refresh the list"
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    // T-21: delete own comment, then reload
    fun deleteComment(commentId: String) {
        if (postId.isEmpty()) return
        viewModelScope.launch {
            try {
                commentRepo.deleteComment(postId, commentId)
                allComments = commentRepo.comments(postId)
                publish()
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't delete the comment"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        private const val PAGE_SIZE = 5
        private const val SEND_TIMEOUT_MS = 10_000L
        const val MAX_COMMENT_LENGTH = 500
    }
}