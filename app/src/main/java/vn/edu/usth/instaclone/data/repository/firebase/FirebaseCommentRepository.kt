package vn.edu.usth.instaclone.data.repository.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.model.Comment
import vn.edu.usth.instaclone.data.repository.CommentRepository

class FirebaseCommentRepository : CommentRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // Newest first (team decision)
    override suspend fun comments(postId: String): List<Comment> {
        val snapshot = db.collection("posts").document(postId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.toObjects(Comment::class.java)
    }

    override suspend fun addComment(postId: String, text: String) {
        val user = auth.currentUser
            ?: throw IllegalStateException("Must be signed in to comment")

        val userDoc = db.collection("users").document(user.uid).get().await()
        val username = userDoc.getString("username") ?: user.displayName ?: "user"

        val postRef = db.collection("posts").document(postId)
        val commentRef = postRef.collection("comments").document()

        val batch = db.batch()
        batch.set(
            commentRef,
            hashMapOf(
                "authorId" to user.uid,
                "authorUsername" to username,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.update(postRef, "commentCount", FieldValue.increment(1))
        batch.commit().await()
    }

    override suspend fun deleteComment(postId: String, commentId: String) {
        val postRef = db.collection("posts").document(postId)
        val batch = db.batch()
        batch.delete(postRef.collection("comments").document(commentId))
        batch.update(postRef, "commentCount", FieldValue.increment(-1))
        batch.commit().await()
    }
}