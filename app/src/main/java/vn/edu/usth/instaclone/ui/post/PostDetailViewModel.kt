package vn.edu.usth.instaclone.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.edu.usth.instaclone.data.model.Comment
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.CommentRepository
import vn.edu.usth.instaclone.data.repository.FakeCommentRepository
import vn.edu.usth.instaclone.data.repository.FakePostRepository
import vn.edu.usth.instaclone.data.repository.PostRepository
import vn.edu.usth.instaclone.data.AppContainer


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

    // Thông báo lỗi (null = không có lỗi)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var postId: String = ""

    // Màn hình gọi hàm này khi mở, truyền id bài cần xem
    fun load(id: String) {
        if (id == postId) return        // đã tải bài này rồi thì bỏ qua
        postId = id
        viewModelScope.launch {
            try {
                _post.value = postRepo.getPost(id)
                _comments.value = commentRepo.comments(id)
            } catch (e: Exception) {
                _errorMessage.value = "Không tải được bài viết"
            }
        }
    }

    // Gửi bình luận rồi tải lại danh sách
    fun sendComment(text: String) {
        val content = text.trim()
        if (content.isEmpty() || postId.isEmpty()) return
        viewModelScope.launch {
            try {
                commentRepo.addComment(postId, content)
                _comments.value = commentRepo.comments(postId)
            } catch (e: Exception) {
                _errorMessage.value = "Không gửi được bình luận"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}