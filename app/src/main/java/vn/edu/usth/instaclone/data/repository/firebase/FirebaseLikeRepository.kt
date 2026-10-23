package vn.edu.usth.instaclone.data.repository.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.repository.LikeRepository

class FirebaseLikeRepository : LikeRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // Has the signed-in user liked this post?
    override suspend fun isLiked(postId: String): Boolean {
        val user = auth.currentUser ?: return false
        val doc = db.collection("posts").document(postId)
            .collection("likes").document(user.uid)
            .get()
            .await()
        return doc.exists()
    }

    // One transaction: create/delete likes/{uid} and change likeCount by +1 / -1.
    // If the state already matches, nothing is written (no double counting).
    override suspend fun setLiked(postId: String, like: Boolean) {
        val user = auth.currentUser
            ?: throw IllegalStateException("Must be signed in to like a post")

        val postRef = db.collection("posts").document(postId)
        val likeRef = postRef.collection("likes").document(user.uid)

        db.runTransaction { tx ->
            val alreadyLiked = tx.get(likeRef).exists()
            if (like && !alreadyLiked) {
                tx.set(likeRef, hashMapOf("createdAt" to FieldValue.serverTimestamp()))
                tx.update(postRef, "likeCount", FieldValue.increment(1))
            } else if (!like && alreadyLiked) {
                tx.delete(likeRef)
                tx.update(postRef, "likeCount", FieldValue.increment(-1))
            }
            null
        }.await()
    }
}