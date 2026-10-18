package vn.edu.usth.instaclone.data.repository

import vn.edu.usth.instaclone.data.model.Comment

interface CommentRepository {
    suspend fun comments(postId: String): List<Comment>
    suspend fun addComment(postId: String, text: String)
    suspend fun  deleteComment(postId: String, commentId: String)
}