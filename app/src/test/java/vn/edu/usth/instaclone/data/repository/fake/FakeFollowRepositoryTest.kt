package vn.edu.usth.instaclone.data.repository.fake

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeFollowRepositoryTest {

    private val users = FakeUserRepository(myId = { "me" }, latencyMillis = 0)

    private fun followRepository(): FakeFollowRepository {
        return FakeFollowRepository(
            myId = { "me" },
            initialFollowing = emptySet(),
            userRepository = users,
        )
    }

    @Test
    fun follow_addsTargetAndUpdatesBothCounters() = runBlocking {
        val repository = followRepository()

        repository.follow("user-tram")

        assertTrue(repository.isFollowing("user-tram"))
        assertEquals(listOf("user-tram"), repository.followingIds())
        assertEquals(13L, users.getUser("me")?.followingCount ?: -1L)
        assertEquals(99L, users.getUser("user-tram")?.followerCount ?: -1L)
    }

    @Test
    fun followTwice_doesNotDoubleCount() = runBlocking {
        val repository = followRepository()

        repository.follow("user-tram")
        repository.follow("user-tram")

        assertEquals(13L, users.getUser("me")?.followingCount ?: -1L)
        assertEquals(99L, users.getUser("user-tram")?.followerCount ?: -1L)
    }

    @Test
    fun unfollow_removesTargetAndUpdatesBothCounters() = runBlocking {
        val repository = followRepository()
        repository.follow("user-tram")

        repository.unfollow("user-tram")

        assertFalse(repository.isFollowing("user-tram"))
        assertEquals(12L, users.getUser("me")?.followingCount ?: -1L)
        assertEquals(98L, users.getUser("user-tram")?.followerCount ?: -1L)
    }

    @Test
    fun unfollowWithoutFollowing_isNoOp() = runBlocking {
        val repository = followRepository()

        repository.unfollow("user-tram")

        assertFalse(repository.isFollowing("user-tram"))
        assertEquals(12L, users.getUser("me")?.followingCount ?: -1L)
        assertEquals(98L, users.getUser("user-tram")?.followerCount ?: -1L)
    }

    @Test
    fun followYourself_throws() {
        val repository = followRepository()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.follow("me") }
        }
    }

    @Test
    fun followUnknownMember_throws() {
        val repository = followRepository()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.follow("missing") }
        }
    }

    @Test
    fun isFollowingYourself_isFalse() = runBlocking {
        val repository = followRepository()

        assertFalse(repository.isFollowing("me"))
    }

    @Test
    fun followingIds_returnsAtMostThirty() = runBlocking {
        val repository = FakeFollowRepository(
            myId = { "me" },
            initialFollowing = emptySet(),
        )
        repeat(31) { index -> repository.follow("target-$index") }

        assertEquals(30, repository.followingIds().size)
    }
}
