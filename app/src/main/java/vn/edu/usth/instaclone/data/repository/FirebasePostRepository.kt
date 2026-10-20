package vn.edu.usth.instaclone.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.model.Post

class FirebasePostRepository : PostRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    override suspend fun feedPage(after: Post?, limit: Int): List<Post> {
        var query = db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())

        if (after != null) {
            val lastDoc = db.collection("posts").document(after.id).get().await()
            if (lastDoc.exists()) {
                query = query.startAfter(lastDoc)
            }
        }

        val snapshot = query.get().await()
        return snapshot.toObjects(Post::class.java)
    }

    override suspend fun followingFeedPage(after: Post?, limit: Int): List<Post> {
        val currentUser = auth.currentUser ?: return emptyList()
        val followingSnapshot = db.collection("users")
            .document(currentUser.uid)
            .collection("following")
            .limit(30)
            .get()
            .await()

        val followedUids = followingSnapshot.documents.map { it.id }
        if (followedUids.isEmpty()) return emptyList()

        var query = db.collection("posts")
            .whereIn("authorId", followedUids)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())

        if (after != null) {
            val lastDoc = db.collection("posts").document(after.id).get().await()
            if (lastDoc.exists()) {
                query = query.startAfter(lastDoc)
            }
        }

        val snapshot = query.get().await()
        return snapshot.toObjects(Post::class.java)
    }

    override suspend fun userPosts(uid: String): List<Post> {
        val snapshot = db.collection("posts")
            .whereEqualTo("authorId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.toObjects(Post::class.java)
    }

    override suspend fun getPost(postId: String): Post? {
        val doc = db.collection("posts").document(postId).get().await()
        return if (doc.exists()) doc.toObject(Post::class.java) else null
    }

    override suspend fun createPost(imageUrl: String, imagePath: String, caption: String): String {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("Must be signed in to create a post")

        val userDoc = db.collection("users").document(currentUser.uid).get().await()
        val authorUsername = userDoc.getString("username") ?: currentUser.displayName ?: "user"
        val authorAvatarUrl = userDoc.getString("avatarUrl") ?: ""

        val postRef = db.collection("posts").document()
        val userRef = db.collection("users").document(currentUser.uid)

        val postData = hashMapOf(
            "authorId" to currentUser.uid,
            "authorUsername" to authorUsername,
            "authorAvatarUrl" to authorAvatarUrl,
            "imageUrl" to imageUrl,
            "imagePath" to imagePath,
            "caption" to caption,
            "likeCount" to 0L,
            "commentCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(postRef, postData)
        batch.update(userRef, "postCount", FieldValue.increment(1))
        batch.commit().await()

        return postRef.id
    }

    override suspend fun deletePost(postId: String) {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("Must be signed in to delete a post")

        val postRef = db.collection("posts").document(postId)
        val postDoc = postRef.get().await()
        if (!postDoc.exists()) return

        val userRef = db.collection("users").document(currentUser.uid)

        val batch = db.batch()
        batch.delete(postRef)
        batch.update(userRef, "postCount", FieldValue.increment(-1))
        batch.commit().await()
    }
}
