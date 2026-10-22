package vn.edu.usth.instaclone.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import vn.edu.usth.instaclone.data.repository.FakePostRepository
import vn.edu.usth.instaclone.data.repository.FakeLikeRepository

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun likeUnlikeStillWorkAfterDeleteMerge() = runTest(dispatcher) {
        val repo = FakePostRepository(myId = { "alice" })
        val vm = HomeFeedViewModel(postRepo = repo, likeRepo = FakeLikeRepository(repo))
        advanceUntilIdle()
        val post = vm.posts.value.first()
        vm.toggleLike(post.id)
        assertTrue(post.id in vm.likedPostIds.value)
        assertEquals(post.likeCount + 1, vm.posts.value.first { it.id == post.id }.likeCount)
        advanceUntilIdle()
        vm.toggleLike(post.id)
        advanceUntilIdle()
        assertFalse(post.id in vm.likedPostIds.value)
        assertEquals(post.likeCount, repo.getPost(post.id)?.likeCount)
    }

    @Test fun deleteRemovesOwnPostWithoutBreakingLikesOnOtherPosts() = runTest(dispatcher) {
        val repo = FakePostRepository(myId = { "alice" })
        val ownId = repo.createPost("https://example.com/test.jpg", "images/posts/alice/test.jpg", "test")
        val vm = HomeFeedViewModel(postRepo = repo, likeRepo = FakeLikeRepository(repo))
        advanceUntilIdle()
        vm.deletePost(ownId)
        advanceUntilIdle()
        assertNull(repo.getPost(ownId))
        assertFalse(vm.posts.value.any { it.id == ownId })
        val remaining = vm.posts.value.first()
        vm.toggleLike(remaining.id)
        advanceUntilIdle()
        assertTrue(remaining.id in vm.likedPostIds.value)
        assertNull(vm.errorMessage.value)
    }
}