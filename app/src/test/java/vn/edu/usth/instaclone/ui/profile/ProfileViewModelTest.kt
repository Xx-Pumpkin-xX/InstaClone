package vn.edu.usth.instaclone.ui.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.usth.instaclone.data.model.Post
import vn.edu.usth.instaclone.data.model.User
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

    private fun viewModel(
        userId: String,
        currentUserId: String,
        postRepository: PostRepository = StubPostRepository(),
        following: Set<String> = emptySet(),
        userRepository: UserRepository = FakeUserRepository(
            myId = { currentUserId },
            latencyMillis = 0,
        ),
    ): ProfileViewModel {
        return ProfileViewModel(
            userId = userId,
            userRepository = userRepository,
            postRepository = postRepository,
            followRepository = FakeFollowRepository(
                myId = { currentUserId },
                initialFollowing = following,
            ),
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

        override suspend fun likePost(postId: String) = Unit

        override suspend fun unlikePost(postId: String) = Unit
    }

    private class FailingUserRepository(private val failure: Exception) : UserRepository {
        override suspend fun claimUsername(uid: String, username: String) = throw failure

        override suspend fun getUser(uid: String): User? = throw failure

        override suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?) = throw failure

        override suspend fun searchByPrefix(prefix: String, limit: Int): List<User> = throw failure
    }
}
