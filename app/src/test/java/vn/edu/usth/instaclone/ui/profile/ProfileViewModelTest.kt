package vn.edu.usth.instaclone.ui.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.FollowRepository
import vn.edu.usth.instaclone.data.repository.PostRepository
import vn.edu.usth.instaclone.data.repository.UserRepository
import vn.edu.usth.instaclone.data.repository.fake.FakeFollowRepository
import vn.edu.usth.instaclone.data.repository.fake.FakeUserRepository
import java.io.IOException

class ProfileViewModelTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun otherProfile_loadsHeaderPostsAndFollowingState() {
        val viewModel = viewModel(
            userId = "user-tram",
            currentUserId = "me",
            postRepository = StubPostRepository(
                postsByUser = mapOf("user-tram" to listOf(testPost("post-1", "user-tram"))),
            ),
            following = setOf("user-tram"),
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isOwnProfile)
        assertFalse(state.notFound)
        assertNull(state.errorMessage)
        assertEquals("tram", state.user?.username)
        assertEquals(listOf("post-1"), state.posts.map { it.id })
        assertTrue(state.isFollowing)
        assertEquals(98L, state.followerCount)
        assertFalse(state.isFollowPending)
        assertNull(state.followError)
    }

    @Test
    fun ownProfile_marksItAsOwn_andDoesNotReadFollowState() {
        val viewModel = viewModel(
            userId = "me",
            currentUserId = "me",
            following = setOf("user-tram"),
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isOwnProfile)
        assertEquals("you", state.user?.username)
        assertFalse(state.isFollowing)
    }

    @Test
    fun unknownUser_setsNotFound() {
        val viewModel = viewModel(userId = "missing", currentUserId = "me")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.notFound)
        assertNull(state.user)
        assertNull(state.errorMessage)
    }

    @Test
    fun networkFailure_showsOfflineMessage() {
        val viewModel = viewModel(
            userId = "user-tram",
            currentUserId = "me",
            userRepository = FailingUserRepository(IOException("offline")),
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ProfileViewModel.OFFLINE_MESSAGE, state.errorMessage)
        assertFalse(state.notFound)
    }

    @Test
    fun otherFailure_showsLoadFailedMessage() {
        val viewModel = viewModel(
            userId = "user-tram",
            currentUserId = "me",
            userRepository = FailingUserRepository(IllegalStateException("boom")),
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ProfileViewModel.LOAD_FAILED_MESSAGE, state.errorMessage)
    }

    @Test
    fun followOtherProfile_updatesButtonAndFollowerCount() {
        val viewModel = viewModel(userId = "user-tram", currentUserId = "me")
        assertEquals(98L, viewModel.uiState.value.followerCount)

        viewModel.toggleFollow()

        val state = viewModel.uiState.value
        assertTrue(state.isFollowing)
        assertEquals(99L, state.followerCount)
        assertEquals(99L, state.user?.followerCount ?: -1L)
        assertFalse(state.isFollowPending)
        assertNull(state.followError)
    }

    @Test
    fun unfollowOtherProfile_updatesButtonAndFollowerCount() {
        val viewModel = viewModel(
            userId = "user-tram",
            currentUserId = "me",
            following = setOf("user-tram"),
        )
        assertTrue(viewModel.uiState.value.isFollowing)
        assertEquals(98L, viewModel.uiState.value.followerCount)

        viewModel.toggleFollow()

        val state = viewModel.uiState.value
        assertFalse(state.isFollowing)
        assertEquals(97L, state.followerCount)
        assertEquals(97L, state.user?.followerCount ?: -1L)
        assertFalse(state.isFollowPending)
    }

    @Test
    fun followFailure_keepsStateAndShowsMessage() {
        val viewModel = viewModel(
            userId = "user-tram",
            currentUserId = "me",
            followRepository = ThrowingFollowRepository(IllegalStateException("boom")),
        )
        val before = viewModel.uiState.value

        viewModel.toggleFollow()

        val state = viewModel.uiState.value
        assertFalse(state.isFollowing)
        assertEquals(before.followerCount, state.followerCount)
        assertFalse(state.isFollowPending)
        assertEquals(ProfileViewModel.FOLLOW_FAILED_MESSAGE, state.followError)
    }

    @Test
    fun followOffline_keepsStateAndShowsOfflineMessage() {
        val viewModel = viewModel(
            userId = "user-tram",
            currentUserId = "me",
            followRepository = ThrowingFollowRepository(IOException("offline")),
        )
        val before = viewModel.uiState.value

        viewModel.toggleFollow()

        val state = viewModel.uiState.value
        assertFalse(state.isFollowing)
        assertEquals(before.followerCount, state.followerCount)
        assertEquals(ProfileViewModel.OFFLINE_MESSAGE, state.followError)
    }

    @Test
    fun followOnOwnProfile_doesNothing() {
        val followRepository = BlockingFollowRepository()
        val viewModel = viewModel(
            userId = "me",
            currentUserId = "me",
            followRepository = followRepository,
        )
        val before = viewModel.uiState.value

        viewModel.toggleFollow()

        assertEquals(0, followRepository.followCalls)
        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun secondTapWhilePending_isIgnored() {
        val followRepository = BlockingFollowRepository()
        val viewModel = viewModel(
            userId = "user-tram",
            currentUserId = "me",
            followRepository = followRepository,
        )

        viewModel.toggleFollow()
        assertTrue(viewModel.uiState.value.isFollowPending)

        viewModel.toggleFollow()
        assertEquals(1, followRepository.followCalls)

        followRepository.gate.complete(Unit)

        val state = viewModel.uiState.value
        assertTrue(state.isFollowing)
        assertEquals(99L, state.followerCount)
        assertFalse(state.isFollowPending)
    }

    @Test
    fun retryWhileFollowPending_doesNotReload() {
        val followRepository = BlockingFollowRepository()
        val viewModel = viewModel(
            userId = "user-tram",
            currentUserId = "me",
            followRepository = followRepository,
        )

        viewModel.toggleFollow()
        viewModel.retry()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isFollowPending)

        followRepository.gate.complete(Unit)
        assertFalse(viewModel.uiState.value.isFollowPending)
    }

    @Test
    fun clearFollowError_clearsMessage() {
        val viewModel = viewModel(
            userId = "user-tram",
            currentUserId = "me",
            followRepository = ThrowingFollowRepository(IllegalStateException("boom")),
        )
        viewModel.toggleFollow()
        assertNotNull(viewModel.uiState.value.followError)

        viewModel.clearFollowError()

        assertNull(viewModel.uiState.value.followError)
    }

    private fun viewModel(
        userId: String,
        currentUserId: String,
        postRepository: PostRepository = StubPostRepository(),
        following: Set<String> = emptySet(),
        userRepository: UserRepository = FakeUserRepository(
            myId = { currentUserId },
            latencyMillis = 0,
        ),
        followRepository: FollowRepository? = null,
    ): ProfileViewModel {
        val resolvedFollowRepository = followRepository ?: FakeFollowRepository(
            myId = { currentUserId },
            initialFollowing = following,
            userRepository = userRepository as? FakeUserRepository,
        )
        return ProfileViewModel(
            userId = userId,
            userRepository = userRepository,
            postRepository = postRepository,
            followRepository = resolvedFollowRepository,
            currentUserId = { currentUserId },
            scope = scope,
        )
    }

    private fun testPost(id: String, authorId: String): Post {
        return Post(id = id, authorId = authorId, authorUsername = "tram", imageUrl = "https://example.com/$id.jpg")
    }

    private class StubPostRepository(
        private val postsByUser: Map<String, List<Post>> = emptyMap(),
    ) : PostRepository {
        override suspend fun feedPage(after: Post?, limit: Int): List<Post> = emptyList()

        override suspend fun followingFeedPage(after: Post?, limit: Int): List<Post> = emptyList()

        override suspend fun userPosts(uid: String): List<Post> = postsByUser[uid].orEmpty()

        override suspend fun getPost(postId: String): Post? = null

        override suspend fun createPost(imageUrl: String, imagePath: String, caption: String): String = ""

        override suspend fun deletePost(postId: String) = Unit
    }

    private class FailingUserRepository(private val failure: Exception) : UserRepository {
        override suspend fun claimUsername(uid: String, username: String) = throw failure

        override suspend fun getUser(uid: String): User? = throw failure

        override suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?) = throw failure

        override suspend fun searchByPrefix(prefix: String, limit: Int): List<User> = throw failure
    }

    private class ThrowingFollowRepository(private val failure: Exception) : FollowRepository {
        override suspend fun isFollowing(targetUid: String): Boolean = false

        override suspend fun follow(targetUid: String) = throw failure

        override suspend fun unfollow(targetUid: String) = throw failure

        override suspend fun followingIds(): List<String> = emptyList()
    }

    private class BlockingFollowRepository : FollowRepository {
        val gate = CompletableDeferred<Unit>()
        var followCalls = 0
            private set

        override suspend fun isFollowing(targetUid: String): Boolean = false

        override suspend fun follow(targetUid: String) {
            followCalls++
            gate.await()
        }

        override suspend fun unfollow(targetUid: String) {
            gate.await()
        }

        override suspend fun followingIds(): List<String> = emptyList()
    }
}
