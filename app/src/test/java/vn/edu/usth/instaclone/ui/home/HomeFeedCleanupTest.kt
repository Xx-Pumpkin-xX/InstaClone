package vn.edu.usth.instaclone.ui.home

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.LikeRepository
import vn.edu.usth.instaclone.data.repository.PostDeletionCleanupException
import vn.edu.usth.instaclone.data.repository.PostRepository

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedCleanupTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<HomeFeedViewModel>()
    private val likes = object : LikeRepository {
        override suspend fun isLiked(postId: String) = false
        override suspend fun setLiked(postId: String, like: Boolean) = Unit
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun model(repo: CleanupRepository) = HomeFeedViewModel(repo, likes).also(models::add)

    @Test fun pendingCleanupDoesNotBlockFeedOrStartDuplicateJobs() = runTest(dispatcher) {
        val repo = CleanupRepository()
        val vm = model(repo)
        runCurrent()
        assertEquals(listOf("post"), vm.posts.value.map { it.id })
        assertFalse(vm.isRefreshing.value)
        assertEquals(1, repo.cleanupCalls)
        vm.refresh()
        runCurrent()
        assertEquals(2, repo.feedCalls)
        assertEquals(1, repo.cleanupCalls)
        repo.gate.complete(Unit)
        advanceUntilIdle()
        assertNull(vm.errorMessage.value)
    }

    @Test fun timeoutLeavesFeedUsableAndAllowsNextRefreshToRetry() = runTest(dispatcher) {
        val repo = CleanupRepository()
        val vm = model(repo)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(vm.isRefreshing.value)
        assertEquals("post", vm.posts.value.single().id)
        assertNotNull(vm.errorMessage.value)
        repo.gate.complete(Unit)
        vm.clearError()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, repo.cleanupCalls)
        assertNull(vm.errorMessage.value)
    }

    @Test fun cleanupFailureRetainsRetryActionWithoutHidingFeed() = runTest(dispatcher) {
        val repo = CleanupRepository().apply {
            failure = PostDeletionCleanupException("deleted", IllegalStateException("network"))
            gate.complete(Unit)
        }
        val vm = model(repo)
        advanceUntilIdle()
        assertEquals("post", vm.posts.value.single().id)
        assertEquals("deleted", vm.cleanupRetryPostId.value)
        vm.retryPostCleanup("deleted")
        advanceUntilIdle()
        assertEquals(listOf("deleted"), repo.deletedIds)
        assertNull(vm.cleanupRetryPostId.value)
        assertNull(vm.errorMessage.value)
    }

    private class CleanupRepository : PostRepository {
        val gate = CompletableDeferred<Unit>()
        var cleanupCalls = 0
        var feedCalls = 0
        var failure: Exception? = null
        val deletedIds = mutableListOf<String>()
        override suspend fun retryPendingPostCleanups() {
            cleanupCalls++
            gate.await()
            failure?.let { throw it }
        }
        override suspend fun feedPage(after: Post?, limit: Int): List<Post> {
            feedCalls++
            return listOf(Post(id = "post"))
        }
        override suspend fun followingFeedPage(after: Post?, limit: Int) = emptyList<Post>()
        override suspend fun userPosts(uid: String) = emptyList<Post>()
        override suspend fun getPost(postId: String): Post? = null
        override suspend fun createPost(imageUrl: String, imagePath: String, caption: String) = "new"
        override suspend fun deletePost(postId: String) { deletedIds += postId }
    }
}
