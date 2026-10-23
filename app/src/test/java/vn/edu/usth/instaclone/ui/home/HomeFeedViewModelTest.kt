package vn.edu.usth.instaclone.ui.home

import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.repository.LikeRepository
import vn.edu.usth.instaclone.data.repository.PostDeletionCleanupException
import vn.edu.usth.instaclone.data.repository.PostRepository

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<HomeFeedViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun model(
        repo: PostRepository,
        likes: LikeRepository = RecordingLikeRepository(),
    ) = HomeFeedViewModel(repo, likes).also(models::add)

    @Test
    fun hasLoadedIsFalseBeforeTheFirstRefresh() {
        val vm = model(StubPostRepository())

        assertFalse(vm.hasLoaded.value)
        assertEquals(FeedMode.ALL, vm.mode.value)
    }

    @Test
    fun refreshLoadsAllModeFromNewestPost() = runTest(dispatcher) {
        val all = posts("all", 1, 10)
        val repo = StubPostRepository().apply { feed = { _, _ -> all } }
        val vm = model(repo)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(FeedMode.ALL, vm.mode.value)
        assertEquals(all.map { it.id }, vm.posts.value.map { it.id })
        assertTrue(vm.hasLoaded.value)
        assertFalse(vm.isRefreshing.value)
        assertFalse(vm.loadFailed.value)
        assertEquals(listOf<Post?>(null), repo.feedCursors)
        assertTrue(repo.followingCursors.isEmpty())
    }

    @Test
    fun selectFollowingClearsTheListAndLoadsOnlyFollowing() = runTest(dispatcher) {
        val all = posts("all", 1, 10)
        val followingPage = posts("following", 1, 4)
        val repo = StubPostRepository().apply {
            feed = { _, _ -> all }
            following = { _, _ -> followingPage }
        }
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()

        vm.selectMode(FeedMode.FOLLOWING)

        assertEquals(FeedMode.FOLLOWING, vm.mode.value)
        assertTrue(vm.posts.value.isEmpty())
        assertFalse(vm.hasLoaded.value)

        advanceUntilIdle()

        assertEquals(followingPage.map { it.id }, vm.posts.value.map { it.id })
        assertTrue(vm.hasLoaded.value)
        assertTrue(repo.followingCursors.all { it == null })
    }

    @Test
    fun selectingTheSameModeDoesNotReload() = runTest(dispatcher) {
        val repo = StubPostRepository().apply { feed = { _, _ -> posts("all", 1, 10) } }
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()

        vm.selectMode(FeedMode.ALL)
        advanceUntilIdle()

        assertEquals(1, repo.feedCursors.size)
        assertTrue(repo.followingCursors.isEmpty())
    }

    @Test
    fun emptyFollowingLoadsWithoutAnError() = runTest(dispatcher) {
        val repo = StubPostRepository()
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()

        vm.selectMode(FeedMode.FOLLOWING)
        advanceUntilIdle()

        assertTrue(vm.posts.value.isEmpty())
        assertTrue(vm.hasLoaded.value)
        assertFalse(vm.loadFailed.value)
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun loadMoreUsesTheSelectedModeCursorAndDropsDuplicates() = runTest(dispatcher) {
        val all = posts("all", 1, 10)
        val nextPage = listOf(all.last(), post("all-11", 100))
        val repo = StubPostRepository().apply {
            feed = { after, _ -> if (after == null) all else nextPage }
        }
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(all.last(), repo.feedCursors[1])
        assertEquals(all.map { it.id } + "all-11", vm.posts.value.map { it.id })
        assertTrue(repo.followingCursors.isEmpty())
    }

    @Test
    fun loadMoreStopsWhenThePageHasNoNewPosts() = runTest(dispatcher) {
        val all = posts("all", 1, 10)
        val repo = StubPostRepository().apply {
            feed = { after, _ -> if (after == null) all else listOf(all.last()) }
        }
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        assertEquals(all.map { it.id }, vm.posts.value.map { it.id })
        assertEquals(2, repo.feedCursors.size)
    }

    @Test
    fun loadMoreDoesNothingBeforeTheFirstLoad() = runTest(dispatcher) {
        val repo = StubPostRepository()
        val vm = model(repo)

        vm.loadMore()
        advanceUntilIdle()

        assertTrue(repo.feedCursors.isEmpty())
        assertFalse(vm.isLoadingMore.value)
    }

    @Test
    fun refreshFailureKeepsLoadedPostsAndMapsOffline() = runTest(dispatcher) {
        val all = posts("all", 1, 10)
        val repo = StubPostRepository().apply { feed = { _, _ -> all } }
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()

        repo.feed = { _, _ -> throw IOException("offline") }
        vm.refresh()
        advanceUntilIdle()

        assertEquals(all.map { it.id }, vm.posts.value.map { it.id })
        assertEquals(HomeFeedViewModel.OFFLINE_MESSAGE, vm.errorMessage.value)
        assertTrue(vm.loadFailed.value)
    }

    @Test
    fun loadMoreFailureKeepsLoadedPostsAndMapsOffline() = runTest(dispatcher) {
        val all = posts("all", 1, 10)
        val repo = StubPostRepository().apply {
            feed = { after, _ ->
                if (after == null) all else throw IOException("offline")
            }
        }
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(all.map { it.id }, vm.posts.value.map { it.id })
        assertEquals(HomeFeedViewModel.OFFLINE_MESSAGE, vm.errorMessage.value)
        assertFalse(vm.isLoadingMore.value)
    }

    @Test
    fun unknownFailureUsesGenericCopy() = runTest(dispatcher) {
        val repo = StubPostRepository().apply {
            feed = { _, _ -> throw IllegalStateException("boom") }
        }
        val vm = model(repo)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(HomeFeedViewModel.LOAD_FAILED_MESSAGE, vm.errorMessage.value)
        assertTrue(vm.loadFailed.value)
        assertTrue(vm.hasLoaded.value)
    }

    @Test
    fun slowLoadMoreFromTheOldModeCannotAppendAfterASwitch() = runTest(dispatcher) {
        val gate = CompletableDeferred<List<Post>>()
        val all = posts("all", 1, 10)
        val followingPage = posts("following", 1, 3)
        val repo = StubPostRepository().apply {
            feed = { after, _ -> if (after == null) all else gate.await() }
            following = { _, _ -> followingPage }
        }
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()

        vm.loadMore()
        runCurrent()
        vm.selectMode(FeedMode.FOLLOWING)
        advanceUntilIdle()

        gate.complete(listOf(post("all-11", 100)))
        advanceUntilIdle()

        assertEquals(followingPage.map { it.id }, vm.posts.value.map { it.id })
        assertFalse(vm.isLoadingMore.value)
    }

    @Test
    fun rapidModeSwitchesKeepOnlyTheLatestMode() = runTest(dispatcher) {
        val gate = CompletableDeferred<List<Post>>()
        val all = posts("all", 1, 10)
        val followingPage = posts("following", 1, 3)
        val repo = StubPostRepository().apply {
            feed = { _, _ -> all }
            following = { _, _ -> gate.await() }
        }
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()

        vm.selectMode(FeedMode.FOLLOWING)
        runCurrent()
        vm.selectMode(FeedMode.ALL)
        advanceUntilIdle()
        gate.complete(followingPage)
        advanceUntilIdle()

        assertEquals(FeedMode.ALL, vm.mode.value)
        assertEquals(all.map { it.id }, vm.posts.value.map { it.id })
    }

    @Test
    fun likedIdsAreReloadedAndResetOnModeSwitch() = runTest(dispatcher) {
        val all = posts("all", 1, 10)
        val followingPage = posts("following", 1, 3)
        val repo = StubPostRepository().apply {
            feed = { _, _ -> all }
            following = { _, _ -> followingPage }
        }
        val likes = RecordingLikeRepository(mutableSetOf("all-1"))
        val vm = model(repo, likes)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(setOf("all-1"), vm.likedPostIds.value)

        vm.selectMode(FeedMode.FOLLOWING)
        advanceUntilIdle()

        assertTrue(vm.likedPostIds.value.isEmpty())
    }

    @Test
    fun modeSwitchPreservesCleanupRetryState() = runTest(dispatcher) {
        val repo = StubPostRepository().apply {
            cleanupFailure = PostDeletionCleanupException("gone", IOException("offline"))
        }
        val vm = model(repo)
        vm.refresh()
        advanceUntilIdle()
        assertEquals("gone", vm.cleanupRetryPostId.value)

        vm.selectMode(FeedMode.FOLLOWING)
        advanceUntilIdle()

        assertEquals("gone", vm.cleanupRetryPostId.value)
        assertEquals(1, repo.cleanupCalls)
    }

    private fun post(id: String, second: Long) =
        Post(id = id, authorId = "author-$id", createdAt = Timestamp(second, 0))

    private fun posts(prefix: String, from: Int, count: Int) =
        (from until from + count).map { post("$prefix-$it", 2_000L - it) }

    private class RecordingLikeRepository(
        private val liked: MutableSet<String> = mutableSetOf(),
    ) : LikeRepository {
        override suspend fun isLiked(postId: String) = postId in liked

        override suspend fun setLiked(postId: String, like: Boolean) {
            if (like) liked.add(postId) else liked.remove(postId)
        }
    }

    private class StubPostRepository : PostRepository {
        var feed: suspend (Post?, Int) -> List<Post> = { _, _ -> emptyList() }
        var following: suspend (Post?, Int) -> List<Post> = { _, _ -> emptyList() }
        var cleanupFailure: Exception? = null
        var cleanupCalls = 0

        val feedCursors = mutableListOf<Post?>()
        val followingCursors = mutableListOf<Post?>()

        override suspend fun feedPage(after: Post?, limit: Int): List<Post> {
            feedCursors += after
            return feed(after, limit)
        }

        override suspend fun followingFeedPage(after: Post?, limit: Int): List<Post> {
            followingCursors += after
            return following(after, limit)
        }

        override suspend fun retryPendingPostCleanups() {
            cleanupCalls++
            cleanupFailure?.let { throw it }
        }

        override suspend fun userPosts(uid: String) = emptyList<Post>()

        override suspend fun getPost(postId: String): Post? = null

        override suspend fun createPost(imageUrl: String, imagePath: String, caption: String) = "new"

        override suspend fun deletePost(postId: String) = Unit
    }
}
