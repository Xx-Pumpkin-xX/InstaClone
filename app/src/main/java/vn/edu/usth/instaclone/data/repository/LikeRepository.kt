package vn.edu.usth.instaclone.data.repository

interface LikeRepository {
    suspend fun isLiked(postId: String): Boolean
    suspend fun setLiked(postId: String, like: Boolean)
}