package vn.edu.usth.instaclone.ui.post

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import vn.edu.usth.instaclone.data.model.Comment
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.CommentRepository
import vn.edu.usth.instaclone.data.repository.PostRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<PostDetailViewModel>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }
    private fun model(repo: Comments) = PostDetailViewModel(Posts(), repo).also(models::add)

    @Test fun sendingRevealsNewCommentAfterAllOlderPages() = runTest(dispatcher) {
        val repo = Comments()
        val vm = model(repo)
        vm.load("post")
        advanceUntilIdle()
        assertEquals(5, vm.comments.value.size)
        assertTrue(vm.hasMore.value)
        var result: Boolean? = null
        vm.sendComment("  newest  ") { result = it }
        advanceUntilIdle()
        assertEquals(true, result)
        assertEquals(8, vm.comments.value.size)
        assertEquals("newest", vm.comments.value.last().text)
        assertFalse(vm.hasMore.value)
    }

    @Test fun failedWriteKeepsVisibleCommentsAndReportsFailure() = runTest(dispatcher) {
        val repo = Comments().apply { failAdd = true }
        val vm = model(repo)
        vm.load("post")
        advanceUntilIdle()
        val original = vm.comments.value
        var result: Boolean? = null
        vm.sendComment("draft") { result = it }
        advanceUntilIdle()
        assertEquals(false, result)
        assertEquals(original, vm.comments.value)
        assertNotNull(vm.errorMessage.value)
    }

    @Test fun deletingOlderCommentReloadsRemainingComments() = runTest(dispatcher) {
        val vm = model(Comments())
        vm.load("post")
        advanceUntilIdle()
        vm.deleteComment("c0")
        advanceUntilIdle()
        assertFalse(vm.comments.value.any { it.id == "c0" })
        assertEquals("c1", vm.comments.value.first().id)
    }

    private class Comments : CommentRepository {
        val items = (0..6).map { Comment(id = "c$it", authorId = "bob", text = "old-$it") }.toMutableList()
        var failAdd = false
        override suspend fun comments(postId: String) = items.toList()
        override suspend fun addComment(postId: String, text: String) {
            if (failAdd) throw IllegalStateException("write rejected")
            items += Comment(id = "new", authorId = "bob", text = text)
        }
        override suspend fun deleteComment(postId: String, commentId: String) {
            items.removeAll { it.id == commentId }
        }
    }
    private class Posts : PostRepository {
        override suspend fun getPost(postId: String) = Post(id = postId)
        override suspend fun feedPage(after: Post?, limit: Int) = emptyList<Post>()
        override suspend fun followingFeedPage(after: Post?, limit: Int) = emptyList<Post>()
        override suspend fun userPosts(uid: String) = emptyList<Post>()
        override suspend fun createPost(imageUrl: String, imagePath: String, caption: String) = "new"
        override suspend fun deletePost(postId: String) = Unit
    }
}
