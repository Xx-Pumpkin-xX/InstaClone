package vn.edu.usth.instaclone.data.repository

import com.google.firebase.Timestamp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.usth.instaclone.data.model.Post

/**
 * Contract tests for the fake, which mirrors [FirebasePostRepository] for
 * previews and unit tests (NFR-15). Covers FR-08 filtering, newest-first
 * ordering, cursor pagination and the 30-follow cap.
 */
class FakePostRepositoryTest {

    private fun repository(
        myId: String = "me-test",
        followedIds: List<String> = emptyList(),
    ) = FakePostRepository(
        myId = { myId },
        followedIds = { followedIds },
    )

    @Test
    fun followingFeed_onlyFollowedAuthors_newestFirst() = runTest {
        val repo = repository(followedIds = listOf("user-tram", "user-phong"))

        val page = repo.followingFeedPage(limit = 20)

        assertEquals(10, page.size)
        assertTrue(page.all { it.authorId == "user-tram" || it.authorId == "user-phong" })
        assertEquals(
            listOf(
                "post-003", "post-006", "post-009", "post-012", "post-015",
                "post-018", "post-021", "post-024", "post-027", "post-030",
            ),
            page.map { it.id },
        )
    }

    @Test
    fun followingFeed_excludesOwnPostsEvenIfFollowed() = runTest {
        val repo = repository(myId = "me-test", followedIds = listOf("me-test", "user-tram"))

        val page = repo.followingFeedPage(limit = 20)

        assertTrue(page.isNotEmpty())
        assertTrue(page.none { it.authorId == "me-test" })
        assertTrue(page.all { it.authorId == "user-tram" })
    }

    @Test
    fun followingFeed_emptyWhenFollowingNobody() = runTest {
        val repo = repository(followedIds = emptyList())

        assertTrue(repo.followingFeedPage(limit = 10).isEmpty())
    }

    @Test
    fun followingFeed_paginatesEveryPostExactlyOnce() = runTest {
        val repo = repository(followedIds = listOf("user-tram", "user-phong"))

        val pages = mutableListOf<List<Post>>()
        var cursor: Post? = null
        while (true) {
            val page = repo.followingFeedPage(after = cursor, limit = 3)
            if (page.isEmpty()) break
            pages += page
            cursor = page.last()
        }

        val all = pages.flatten()
        assertEquals(listOf(3, 3, 3, 1), pages.map { it.size })
        assertEquals(10, all.size)
        assertEquals(10, all.map { it.id }.toSet().size)
        assertEquals("post-030", all.last().id)
    }

    @Test
    fun followingFeed_usesOnlyThirtyFollowedAuthors() = runTest {
        var author = "author-0"
        val followed = (0 until 31).map { "author-$it" }
        val repo = FakePostRepository(
            myId = { author },
            followedIds = { followed },
        )
        repeat(31) { index ->
            author = "author-$index"
            repo.createPost("", "images/posts/author-$index/post-$index.jpg", "caption")
        }
        author = "author-30"

        val page = repo.followingFeedPage(limit = 100)

        assertEquals(30, page.size)
        assertTrue(page.none { it.authorId == "author-30" })
        assertEquals(30, page.map { it.authorId }.toSet().size)
    }

    @Test
    fun feedPage_paginatesEveryPostExactlyOnce_newestFirst() = runTest {
        val repo = repository()

        val all = mutableListOf<Post>()
        var cursor: Post? = null
        while (true) {
            val page = repo.feedPage(after = cursor, limit = 5)
            if (page.isEmpty()) break
            all += page
            cursor = page.last()
        }

        assertEquals(36, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(isNewestFirst(all))
    }

    @Test
    fun deletedCursor_doesNotRestartAtFirstPage() = runTest {
        val repo = repository()
        val firstPage = repo.feedPage(limit = 10)
        val deletedCursor = firstPage.last()
        repo.deletePost(deletedCursor.id)

        val nextPage = repo.feedPage(after = deletedCursor, limit = 10)

        assertTrue(nextPage.isNotEmpty())
        val firstPageIds = firstPage.map { it.id }.toSet()
        assertTrue(nextPage.none { it.id in firstPageIds })
        assertTrue(isNewestFirst(nextPage))
    }

    @Test
    fun cursorWithoutCreatedAt_returnsEmpty() = runTest {
        val repo = repository(followedIds = listOf("user-tram"))
        val ghost = Post(id = "ghost")

        assertTrue(repo.feedPage(after = ghost, limit = 10).isEmpty())
        assertTrue(repo.followingFeedPage(after = ghost, limit = 10).isEmpty())
    }

    @Test
    fun nonPositiveLimit_returnsEmpty() = runTest {
        val repo = repository(followedIds = listOf("user-tram"))

        assertTrue(repo.feedPage(limit = 0).isEmpty())
        assertTrue(repo.feedPage(limit = -1).isEmpty())
        assertTrue(repo.followingFeedPage(limit = 0).isEmpty())
        assertFalse(repo.feedPage(limit = 1).isEmpty())
    }

    private fun isNewestFirst(posts: List<Post>): Boolean =
        posts.zipWithNext().all { (a, b) ->
            val left = a.createdAt ?: return@all false
            val right = b.createdAt ?: return@all false
            left.seconds > right.seconds ||
                (left.seconds == right.seconds && left.nanoseconds >= right.nanoseconds)
        }
}
