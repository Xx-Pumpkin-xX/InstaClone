package vn.edu.usth.instaclone.data.repository

import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Post
import java.util.UUID

class FakePostRepository(
    private val myId: () -> String = {
        AppContainer.authRepository.currentUserId.value ?: "me"
    },
    /** Followed account IDs, newest follow first, as in [FollowRepository.followingIds]. */
    private val followedIds: suspend () -> List<String> = { emptyList() },
) : PostRepository {
    private val posts = mutableListOf<Post>()
    private var seededMyPosts = false

    init {
        // Bài mẫu gốc
        val first = Post(
            id = "post-001",
            authorId = "user-nam",
            authorUsername = "dev",
            authorAvatarUrl = "https://picsum.photos/id/64/200/200",
            imageUrl = "https://picsum.photos/id/10/1080/1080",
            imagePath = "images/posts/user-nam/post-001.jpg",
            caption = "Welcome to InstaClone!",
            likeCount = 12,
            commentCount = 3,
            createdAt = Timestamp.now()
        )
        posts.add(first)

        // Danh sách tên người đăng để xoay vòng
        val names = listOf("tram", "trang", "nam", "phong", "dat", "thinh")
        val captions = listOf(
            "Cuối tuần vui vẻ",
            "Hôm nay trời đẹp quá",
            "Cà phê sáng thứ Hai",
            "Chuyến đi đáng nhớ",
            "Làm việc cả ngày, mệt nhưng vui",
            "Món mới nấu thử",
            "Hoàng hôn hôm nay",
            "Chill cùng bạn bè",
            "Good vibes only",
            "Một ngày bình thường",
            "Biển ƠI",
            "PreX là valo champion 2026 "
        )
        // Thêm 29 bài mẫu nữa (tổng 30) để thử phân trang.
        for (i in 2..30) {
            val name = names[i % names.size]
            posts.add(
                first.copy(
                    id = "post-%03d".format(i),
                    authorId = "user-$name",
                    authorUsername = name,
                    authorAvatarUrl = "https://picsum.photos/id/${20 + i}/200/200",
                    imageUrl = "https://picsum.photos/id/${10 + i}/1080/1080",
                    caption = captions.random(),
                    likeCount = (0..500).random().toLong(),
                    commentCount = (0..40).random().toLong(),
                    createdAt = Timestamp(Timestamp.now().seconds - i * 2700L, 0)
                )
            )
        }
    }

    override suspend fun feedPage(after: Post?, limit: Int): List<Post> {
        ensureMyPosts()
        delay(if (after == null) 300 else 1500)
        return page(sortedPosts(), after, limit)
    }

    override suspend fun followingFeedPage(after: Post?, limit: Int): List<Post> {
        ensureMyPosts()
        delay(300)
        if (limit <= 0) return emptyList()

        val me = myId()
        val followed = followedIds()
            .filter { it != me }
            .take(MAX_FOLLOWED)
            .toSet()
        if (followed.isEmpty()) return emptyList()

        val byFollowedAuthors = sortedPosts().filter { it.authorId in followed }
        return page(byFollowedAuthors, after, limit)
    }

    /**
     * Newest first, with ties broken by document ID ascending, matching
     * Firestore's ordering of equal field values.
     */
    private fun sortedPosts(): List<Post> = posts.sortedWith(
        compareByDescending<Post> { it.createdAt?.seconds ?: Long.MIN_VALUE }
            .thenByDescending { it.createdAt?.nanoseconds ?: 0 }
            .thenBy { it.id }
    )

    /**
     * Exclusive cursor pagination, mirroring [FirebasePostRepository]:
     * the cursor is the pair `(createdAt, documentId)` with `createdAt`
     * descending and document ID ascending. A deleted cursor therefore
     * resumes exactly after its position instead of skipping same-timestamp
     * posts.
     */
    private fun page(all: List<Post>, after: Post?, limit: Int): List<Post> {
        if (limit <= 0) return emptyList()
        if (after == null) return all.take(limit)

        val cursorCreatedAt = after.createdAt ?: return emptyList()
        return all.filter { isAfterCursor(it, cursorCreatedAt, after.id) }.take(limit)
    }

    private fun isAfterCursor(post: Post, cursorCreatedAt: Timestamp, cursorId: String): Boolean {
        val createdAt = post.createdAt ?: return false
        if (createdAt.seconds != cursorCreatedAt.seconds) {
            return createdAt.seconds < cursorCreatedAt.seconds
        }
        if (createdAt.nanoseconds != cursorCreatedAt.nanoseconds) {
            return createdAt.nanoseconds < cursorCreatedAt.nanoseconds
        }
        return post.id > cursorId
    }

    override suspend fun userPosts(uid: String): List<Post> {
        ensureMyPosts()
        delay(300)
        return posts
            .filter { it.authorId == uid }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    override suspend fun getPost(postId: String): Post? {
        delay(200)
        return posts.find { it.id == postId }
    }

    override suspend fun createPost(imageUrl: String, imagePath: String, caption: String): String {
        delay(500) // simulate network request
        val me = myId()
        val newPostId = "post-${UUID.randomUUID().toString().take(8)}"
        val newPost = Post(
            id = newPostId,
            authorId = me,
            authorUsername = if (me == "me") "nam_dev" else "you",
            authorAvatarUrl = "https://picsum.photos/id/64/200/200",
            imageUrl = imageUrl,
            imagePath = imagePath,
            caption = caption,
            likeCount = 0,
            commentCount = 0,
            createdAt = Timestamp.now()
        )
        posts.add(0, newPost)
        return newPostId
    }

    override suspend fun deletePost(postId: String) {
        delay(400)
        posts.removeAll { it.id == postId }
    }


    // Tăng hoặc giảm số bình luận của một bài (dùng bởi FakeCommentRepository)
    fun addToCommentCount(postId: String, delta: Long) {
        val i = posts.indexOfFirst { it.id == postId }
        if (i != -1) {
            posts[i] = posts[i].copy(
                commentCount = (posts[i].commentCount + delta).coerceAtLeast(0)
            )
        }
    }

    // Tăng hoặc giảm số like của một bài (dùng bởi FakeLikeRepository)
    fun addToLikeCount(postId: String, delta: Long) {
        val i = posts.indexOfFirst { it.id == postId }
        if (i != -1) {
            posts[i] = posts[i].copy(
                likeCount = (posts[i].likeCount + delta).coerceAtLeast(0)
            )
        }
    }
    /**
     * The signed-in member gets a few demo posts so their profile grid is not empty.
     * Called lazily because auth may not have finished when this repository is created.
     */
    private fun ensureMyPosts() {
        if (seededMyPosts) return
        seededMyPosts = true
        val me = myId()
        if (posts.any { it.authorId == me }) return

        val captions = listOf(
            "My first post",
            "Building the profile screen",
            "Weekend walk",
            "Coffee break",
            "Sunset",
            "Good vibes",
        )
        captions.forEachIndexed { index, caption ->
            posts.add(
                Post(
                    id = "post-me-%02d".format(index + 1),
                    authorId = me,
                    authorUsername = if (me == "me") "nam_dev" else "you",
                    authorAvatarUrl = "https://picsum.photos/id/64/200/200",
                    imageUrl = "https://picsum.photos/id/${30 + index}/1080/1080",
                    imagePath = "images/posts/$me/post-me-%02d.jpg".format(index + 1),
                    caption = caption,
                    likeCount = (3..80).random().toLong(),
                    commentCount = (0..12).random().toLong(),
                    createdAt = Timestamp(Timestamp.now().seconds - index * 3600L, 0),
                )
            )
        }
    }

    private companion object {
        /** A Firestore `in` query accepts at most 30 values (A-08). */
        const val MAX_FOLLOWED = 30
    }

}
