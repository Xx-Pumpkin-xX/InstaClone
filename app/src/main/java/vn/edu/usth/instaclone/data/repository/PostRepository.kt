package vn.edu.usth.instaclone.data.repository

import vn.edu.usth.instaclone.data.model.Post

interface PostRepository {
    suspend fun feedPage(after: Post? = null, limit: Int = 10): List<Post>
    suspend fun followingFeedPage(after: Post? = null, limit: Int = 10): List<Post>
    suspend fun userPosts(uid: String): List<Post>
    suspend fun getPost(postId: String): Post?
    suspend fun createPost(imageUrl: String, imagePath: String, caption: String): String
    suspend fun deletePost(postId: String)
}