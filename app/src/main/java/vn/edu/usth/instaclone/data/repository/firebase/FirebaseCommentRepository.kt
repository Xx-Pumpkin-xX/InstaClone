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

    // FR-11/FR-12: oldest first, so new comments appear at the end.
    override suspend fun comments(postId: String): List<Comment> {
        val snapshot = db.collection("posts").document(postId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .await()
        return snapshot.toObjects(Comment::class.java)
    }

    override suspend fun addComment(postId: String, text: String) {
        val user = auth.currentUser
            ?: throw IllegalStateException("Must be signed in to comment")

        val content = text.trim()
        require(content.length in 1..500) { "Comment must contain 1-500 characters" }

        val userDoc = db.collection("users").document(user.uid).get().await()
        check(userDoc.exists()) { "Member profile not found" }
        val username = checkNotNull(userDoc.getString("username")) { "Member username not found" }

        val postRef = db.collection("posts").document(postId)
        val commentRef = postRef.collection("comments").document()

        val batch = db.batch()
        batch.set(
            commentRef,
            hashMapOf(
                "authorId" to user.uid,
                "authorUsername" to username,
                "text" to content,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.update(postRef, mapOf(
            "commentCount" to FieldValue.increment(1),
            "commentMutation" to mapOf("kind" to "comment", "id" to commentRef.id),
        ))
        batch.commit().await()
    }

    override suspend fun deleteComment(postId: String, commentId: String) {
        val postRef = db.collection("posts").document(postId)
        val batch = db.batch()
        batch.delete(postRef.collection("comments").document(commentId))
        batch.update(postRef, mapOf(
            "commentCount" to FieldValue.increment(-1),
            "commentMutation" to mapOf("kind" to "comment", "id" to commentId),
        ))
        batch.commit().await()
    }
}