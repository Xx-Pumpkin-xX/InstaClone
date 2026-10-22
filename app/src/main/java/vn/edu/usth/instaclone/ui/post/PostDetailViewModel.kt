package vn.edu.usth.instaclone.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Comment
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.CommentRepository
import vn.edu.usth.instaclone.data.repository.PostRepository


class PostDetailViewModel(
    private val postRepo: PostRepository = AppContainer.postRepository,
    private val commentRepo: CommentRepository = AppContainer.commentRepository,
): ViewModel(){
    // Bai dang xem ( null = chua tai xong hoac ko tim thay)
    private val _post=MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post

    // Danh sách bình luận
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    // MỚI: true = còn bình luận chưa hiện, có thể tải thêm
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore
    // Thông báo lỗi (null = không có lỗi)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // MỚI: toàn bộ bình luận của bài (trong "kho")
    private var allComments: List<Comment> = emptyList()
    // MỚI: đang cho hiện bao nhiêu bình luận
    private var shown = PAGE_SIZE

    // MỚI: cắt allComments lấy phần đầu để hiện ra màn hình
    private fun publish() {
        _comments.value = allComments.take(shown)      // chỉ lấy `shown` cái đầu
        _hasMore.value = allComments.size > shown      // còn dư thì còn tải thêm được
    }

    // MỚI: màn hình gọi khi kéo gần cuối
    fun loadMoreComments() {
        if (!_hasMore.value) return    // hết rồi thì thôi
        shown += PAGE_SIZE             // hiện thêm 5
        publish()
    }

    private var postId: String = ""

    // Màn hình gọi hàm này khi mở, truyền id bài cần xem
    fun load(id: String) {
        if (id == postId) return        // đã tải THÀNH CÔNG bài này rồi thì bỏ qua
        viewModelScope.launch {
            try {
                val loadedPost = postRepo.getPost(id)
                val loadedComments = commentRepo.comments(id)
                // Cả hai đều thành công mới cập nhật
                postId = id
                _post.value = loadedPost
                allComments = loadedComments
                shown = PAGE_SIZE
                publish()
            } catch (e: Exception) {
                _errorMessage.value = "Không tải được bài viết"
            }
        }
    }

    // Gửi bình luận; báo kết quả qua onResult (true = thành công, false = thất bại)
    // Gửi bình luận; báo kết quả qua onResult (true = thành công, false = thất bại)
    fun sendComment(text: String, onResult: (Boolean) -> Unit = {}) {
        val content = text.trim()
        if (content.isEmpty() || content.length > MAX_COMMENT_LENGTH || postId.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                commentRepo.addComment(postId, content)
                allComments = commentRepo.comments(postId)
                shown += 1
                publish()
                onResult(true)
            } catch (e: Exception) {
                _errorMessage.value = "Không gửi được bình luận"
                onResult(false)
            }
        }
    }

    //(T-21): xóa bình luận của mình rồi tải lại danh sách
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

    // MỚI: hằng số, mỗi lần hiện 5 bình luận
    companion object {
        private const val PAGE_SIZE = 5
        const val MAX_COMMENT_LENGTH = 500
    }
}