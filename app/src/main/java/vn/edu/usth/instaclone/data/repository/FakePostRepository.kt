package vn.edu.usth.instaclone.data.repository

import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import vn.edu.usth.instaclone.data.model.Post
import java.util.UUID

class FakePostRepository : PostRepository {
    private val posts = mutableListOf<Post>()

    init {
        // Sample seed post for testing
        posts.add(
            Post(
                id = "post-001",
                authorId = "user-nam",
                authorUsername = "nam_dev",
                authorAvatarUrl = "https://picsum.photos/id/64/200/200",
                imageUrl = "https://picsum.photos/id/10/1080/1080",
                imagePath = "images/posts/user-nam/post-001.jpg",
                caption = "Welcome to InstaClone!",
                likeCount = 12,
                commentCount = 3,
                createAt = Timestamp.now()
            )
        )
    }

    override suspend fun feedPage(after: Post?, limit: Int): List<Post> {
        delay(300)
        return posts.take(limit)
    }

    override suspend fun followFeedPage(after: Post?, limit: Int): List<Post> {
        delay(300)
        return posts.take(limit)
    }

    override suspend fun userPosts(uid: String): List<Post> {
        delay(300)
        return posts.filter { it.authorId == uid }
    }

    override suspend fun getPost(postId: String): Post? {
        delay(200)
        return posts.find { it.id == postId }
    }

    override suspend fun createPost(imageUrl: String, imagePath: String, caption: String): String {
        delay(500) // simulate network request
        val newPostId = "post-${UUID.randomUUID().toString().take(8)}"
        val newPost = Post(
            id = newPostId,
            authorId = "user-nam",
            authorUsername = "nam_dev",
            authorAvatarUrl = "https://picsum.photos/id/64/200/200",
            imageUrl = imageUrl,
            imagePath = imagePath,
            caption = caption,
            likeCount = 0,
            commentCount = 0,
            createAt = Timestamp.now()
        )
        posts.add(0, newPost)
        return newPostId
    }

    override suspend fun deletePost(postId: String) {
        delay(400)
        posts.removeAll { it.id == postId }
    }
}
