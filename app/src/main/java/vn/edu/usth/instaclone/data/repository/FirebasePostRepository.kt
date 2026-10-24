package vn.edu.usth.instaclone.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.model.Post

class FirebasePostRepository(
    private val imageStorage: ImageStorage,
    private val followRepository: FollowRepository,
) : PostRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val deletionMutex = Mutex()

    /**
     * Returns the newest posts from everybody.
     *
     * Pages are ordered by `createdAt` descending only. The document ID is the
     * implicit tie-breaker, but adding an explicit `orderBy(documentId())` would
     * require an extra composite index, so the deleted-cursor case is handled by
     * [runPageAfter] instead.
     */
    override suspend fun feedPage(after: Post?, limit: Int): List<Post> {
        if (limit <= 0) return emptyList()

        val query = db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        return if (after == null) {
            runQuery(query.limit(limit.toLong()), "feedPage")
        } else {
            runPageAfter(query, after, limit, "feedPage")
        }
    }

    override suspend fun followingFeedPage(after: Post?, limit: Int): List<Post> {
        if (limit <= 0) return emptyList()
        val currentUser = auth.currentUser ?: return emptyList()

        // A Firestore `in` query accepts at most 30 values (A-08). The follow
        // repository returns the newest follows first, so the cap is deterministic.
        val followedUids = followRepository.followingIds()
            .filter { it != currentUser.uid }
            .take(MAX_IN_VALUES)
        if (followedUids.isEmpty()) return emptyList()

        val query = db.collection("posts")
            .whereIn("authorId", followedUids)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        return if (after == null) {
            runQuery(query.limit(limit.toLong()), "followingFeedPage")
        } else {
            runPageAfter(query, after, limit, "followingFeedPage")
        }
    }

    /**
     * Returns the posts strictly after [after] in `(createdAt DESC, documentId ASC)`
     * order, without an explicit document-ID `orderBy`.
     *
     * `startAt(cursorTime)` is inclusive, so it includes every post sharing the
     * cursor's timestamp, even ones already shown. Posts at or before the cursor
     * position are filtered out using the document ID as the tie-breaker. The
     * first query asks for one extra document, so a normal page with no ties is
     * still a single read; if the timestamp block is larger, the loop continues
     * after the last document it saw.
     */
    private suspend fun runPageAfter(
        baseQuery: Query,
        after: Post,
        limit: Int,
        context: String,
    ): List<Post> {
        val cursorTime = after.createdAt ?: return emptyList()
        val result = mutableListOf<Post>()
        var pageCursor: DocumentSnapshot? = null

        while (result.size < limit) {
            val remaining = limit - result.size
            val requested = if (pageCursor == null) remaining + 1 else remaining
            val query = if (pageCursor == null) {
                baseQuery.startAt(cursorTime).limit(requested.toLong())
            } else {
                baseQuery.startAfter(pageCursor).limit(requested.toLong())
            }
            val snapshot = runQuerySnapshot(query, context)
            if (snapshot.isEmpty) break

            for (document in snapshot.documents) {
                val post = document.toObject(Post::class.java) ?: continue
                if (!isAfterCursor(post, cursorTime, after.id)) continue
                result += post
                if (result.size == limit) return result
            }

            if (snapshot.size() < requested) break
            pageCursor = snapshot.documents.last()
        }

        return result
    }

    private fun isAfterCursor(post: Post, cursorTime: Timestamp, cursorId: String): Boolean {
        val createdAt = post.createdAt ?: return false
        if (createdAt.seconds != cursorTime.seconds) {
            return createdAt.seconds < cursorTime.seconds
        }
        if (createdAt.nanoseconds != cursorTime.nanoseconds) {
            return createdAt.nanoseconds < cursorTime.nanoseconds
        }
        return post.id > cursorId
    }

    /** Runs a page query, logging failures (missing index, permissions) before rethrowing. */
    private suspend fun runQuery(query: Query, context: String): List<Post> {
        return runQuerySnapshot(query, context).toObjects(Post::class.java)
    }

    private suspend fun runQuerySnapshot(query: Query, context: String): QuerySnapshot {
        return try {
            query.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A missing composite index fails here (FAILED_PRECONDITION). Log it so
            // the console URL is visible instead of only the generic snackbar copy.
            Log.w(TAG, "$context query failed", e)
            throw e
        }
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
                "counterMutation" to mapOf("kind" to "post", "id" to postRef.id),
            ),
        )
        batch.commit().await()

        return postRef.id
    }

    override suspend fun deletePost(postId: String) =
        deletionMutex.withLock { deletePostLocked(postId) }

    private suspend fun deletePostLocked(postId: String) {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("Must be signed in to delete a post")

        val postRef = db.collection("posts").document(postId)
        val cleanupRef = db.collection("postDeletionCleanup").document(postId)
        val postDoc = postRef.get().await()
        val cleanupSnapshot = if (postDoc.exists()) {
            null
        } else {
            try {
                cleanupRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw PostDeletionCleanupException(postId, e)
            }
        }
        if (!postDoc.exists() && cleanupSnapshot?.exists() != true) return

        if (postDoc.exists()) {
            check(postDoc.getString("authorId") == currentUser.uid) {
                "Can only delete your own post"
            }
            val imagePath = postDoc.getString("imagePath").orEmpty()
            if (imagePath.isNotBlank()) {
                val validImagePath = Regex(
                    "^images/posts/${Regex.escape(currentUser.uid)}/[A-Za-z0-9_-]+\\.(jpg|png)$"
                )
                require(validImagePath.matches(imagePath)) {
                    "Post image path does not belong to its author"
                }
            }

            val userRef = db.collection("users").document(currentUser.uid)
            val batch = db.batch()
            batch.delete(postRef)
            batch.update(
                userRef,
                mapOf(
                    "postCount" to FieldValue.increment(-1),
                    "counterMutation" to mapOf("kind" to "post", "id" to postId),
                ),
            )
            batch.set(
                cleanupRef,
                mapOf(
                    "authorId" to currentUser.uid,
                    "imagePath" to imagePath,
                ),
            )
            batch.commit().await()
        }

        try {
            val cleanup = if (postDoc.exists()) {
                cleanupRef.get().await()
            } else {
                requireNotNull(cleanupSnapshot)
            }
            check(cleanup.getString("authorId") == currentUser.uid) {
                "Can only clean up your own deleted post"
            }
            val likes = postRef.collection("likes").get().await().documents
            val comments = postRef.collection("comments").get().await().documents
            (likes + comments).chunked(MAX_BATCH_WRITES).forEach { documents ->
                val childBatch = db.batch()
                documents.forEach { document -> childBatch.delete(document.reference) }
                childBatch.commit().await()
            }

            cleanup.getString("imagePath")
                ?.takeIf(String::isNotBlank)
                ?.let { imageStorage.delete(it) }
            cleanupRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PostDeletionCleanupException(postId, e)
        }
    }

    override suspend fun retryPendingPostCleanups() {
        val uid = auth.currentUser?.uid ?: return
        val pending = db.collection("postDeletionCleanup")
            .whereEqualTo("authorId", uid)
            .get()
            .await()
        pending.documents.forEach { document -> deletePost(document.id) }
    }

    private companion object {
        const val MAX_BATCH_WRITES = 500

        /** A Firestore `in` query accepts at most 30 values (A-08). */
        const val MAX_IN_VALUES = 30

        const val TAG = "FirebasePostRepository"
    }
}

class PostDeletionCleanupException(val postId: String, cause: Exception) :
    Exception("Post deleted, but related data could not be cleaned up. Retry cleanup.", cause)