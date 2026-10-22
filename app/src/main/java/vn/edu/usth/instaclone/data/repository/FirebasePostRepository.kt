package vn.edu.usth.instaclone.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.model.Post

class FirebasePostRepository(
    private val imageStorage: ImageStorage,
) : PostRepository {
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
        check(userDoc.exists()) { "Member profile not found" }
        val authorUsername = userDoc.getString("username")
            ?: throw IllegalStateException("Member username not found")
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
        batch.update(
            userRef,
            mapOf(
                "postCount" to FieldValue.increment(1),
                "postMutationId" to postRef.id,
            ),
        )
        batch.commit().await()

        return postRef.id
    }

    override suspend fun deletePost(postId: String) {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("Must be signed in to delete a post")

        val postRef = db.collection("posts").document(postId)
        val postDoc = postRef.get().await()
        if (!postDoc.exists()) return

        check(postDoc.getString("authorId") == currentUser.uid) {
            "Can only delete your own post"
        }
        val imagePath = postDoc.getString("imagePath").orEmpty()
        if (imagePath.isNotBlank()) {
            require(imagePath.startsWith("images/posts/${currentUser.uid}/")) {
                "Post image path does not belong to its author"
            }
        }

        val likes = postRef.collection("likes").get().await().documents
        val comments = postRef.collection("comments").get().await().documents
        (likes + comments).chunked(MAX_BATCH_WRITES).forEach { documents ->
            val childBatch = db.batch()
            documents.forEach { document -> childBatch.delete(document.reference) }
            childBatch.commit().await()
        }

        val userRef = db.collection("users").document(currentUser.uid)

        val batch = db.batch()
        batch.delete(postRef)
        batch.update(
            userRef,
            mapOf(
                "postCount" to FieldValue.increment(-1),
                "postMutationId" to postId,
            ),
        )
        batch.commit().await()

        if (imagePath.isNotBlank()) {
            try {
                imageStorage.delete(imagePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw PostImageCleanupException(e)
            }
        }
    }

    private companion object {
        const val MAX_BATCH_WRITES = 500
    }
}

class PostImageCleanupException(cause: Exception) :
    Exception("Post deleted, but its photo could not be removed", cause)