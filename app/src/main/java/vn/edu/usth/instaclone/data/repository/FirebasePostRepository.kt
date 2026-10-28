package vn.edu.usth.instaclone.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.model.Post
import java.util.UUID

class FirebasePostRepository(
    private val imageStorage: ImageStorage,
    private val followRepository: FollowRepository,
) : PostRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val deletionMutex = Mutex()

    init {
        Firebase.storage.maxUploadRetryTimeMillis = STORAGE_MAX_RETRY_TIME_MILLIS
    }

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
     * Returns the posts strictly after [after] in `(createdAt DESC, documentId DESC)`
     * order, without an explicit document-ID `orderBy`.
     *
     * Firestore's implicit document-ID tie-breaker follows the last explicit
     * orderBy, so equal timestamps come back in descending document-ID order.
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
        return post.id < cursorId
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

    override suspend fun createPost(imageUrl: String, imagePath: String, caption: String): String =
        createPostWithId(UUID.randomUUID().toString(), imageUrl, imagePath, caption)

    override suspend fun createPostWithId(
        postId: String,
        imageUrl: String,
        imagePath: String,
        caption: String
    ): String {
        val currentUser = auth.currentUser ?: throw PostCreationException(
            PostCreationException.CommitOutcome.NOT_COMMITTED,
            IllegalStateException("Must be signed in to create a post")
        )
        val postRef = db.collection("posts").document(postId)
        val userRef = db.collection("users").document(currentUser.uid)
        return try {
            if (!postId.matches(Regex("^[A-Za-z0-9_-]+$"))) {
                throw DefinitePostRejection("Invalid post ID")
            }
            if (!imagePath.matches(
                    Regex("^images/posts/${Regex.escape(currentUser.uid)}/[A-Za-z0-9_-]+\\.(jpg|png)$")
                )
            ) {
                throw DefinitePostRejection("Post image path does not belong to its author")
            }

            db.runTransaction { transaction ->
                val existingPost = transaction.get(postRef)
                if (existingPost.exists()) {
                    if (!(
                        existingPost.getString("authorId") == currentUser.uid &&
                            existingPost.getString("imagePath") == imagePath &&
                            existingPost.getString("imageUrl") == imageUrl &&
                            existingPost.getString("caption") == caption
                    )) {
                        throw DefinitePostRejection(
                            "Post submission ID is already used by different content"
                        )
                    }
                    return@runTransaction postId
                }

                val userDoc = transaction.get(userRef)
                if (!userDoc.exists()) throw DefinitePostRejection("Member profile not found")
                val authorUsername = userDoc.getString("username")
                    ?: throw DefinitePostRejection("Member username not found")
                val authorAvatarUrl = userDoc.getString("avatarUrl") ?: ""
                transaction.set(
                    postRef,
                    hashMapOf(
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
                )
                transaction.update(
                    userRef,
                    mapOf(
                        "postCount" to FieldValue.increment(1),
                        "counterMutation" to mapOf("kind" to "post", "id" to postId),
                    ),
                )
                postId
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            var postLookupSucceeded = false
            val existingPost = try {
                postRef.get().await().also { postLookupSucceeded = true }
            } catch (verificationError: CancellationException) {
                throw verificationError
            } catch (verificationError: Exception) {
                e.addSuppressed(verificationError)
                null
            }
            if (existingPost?.exists() == true) {
                val matchesSubmission =
                    existingPost.getString("authorId") == currentUser.uid &&
                        existingPost.getString("imagePath") == imagePath &&
                        existingPost.getString("imageUrl") == imageUrl &&
                        existingPost.getString("caption") == caption
                if (matchesSubmission) return postId

                e.addSuppressed(
                    IllegalStateException("An existing post with this ID has different content")
                )
            }

            val outcome = if (
                postLookupSucceeded &&
                existingPost?.exists() != true &&
                e.isDefinitePostRejection()
            ) {
                PostCreationException.CommitOutcome.NOT_COMMITTED
            } else {
                PostCreationException.CommitOutcome.UNKNOWN
            }
            throw PostCreationException(outcome, e)
        }
    }

    private fun Throwable.isDefinitePostRejection(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is DefinitePostRejection) {
                return true
            }
            if (cause is FirebaseFirestoreException) {
                return cause.code in setOf(
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    FirebaseFirestoreException.Code.UNAUTHENTICATED,
                    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION,
                    FirebaseFirestoreException.Code.NOT_FOUND,
                    FirebaseFirestoreException.Code.ALREADY_EXISTS
                )
            }
            cause = cause.cause
        }
        return false
    }

    private class DefinitePostRejection(message: String) : IllegalStateException(message)

    override suspend fun deletePost(postId: String) =
        deletionMutex.withLock { deletePostLocked(postId) }

    private suspend fun deletePostLocked(postId: String) {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("Must be signed in to delete a post")

        val postRef = db.collection("posts").document(postId)
        val cleanupRef = db.collection("postDeletionCleanup").document(postId)
        val postDoc = postRef.get(Source.SERVER).await()
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
            try {
                withTimeout(10_000L) {
                    db.runTransaction { tx ->
                        val fresh = tx.get(postRef)
                        if (!fresh.exists()) return@runTransaction null
                        tx.delete(postRef)
                        tx.update(userRef, mapOf(
                            "postCount" to FieldValue.increment(-1),
                            "counterMutation" to mapOf("kind" to "post", "id" to postId),
                        ))
                        tx.set(cleanupRef, mapOf("authorId" to currentUser.uid, "imagePath" to imagePath))
                        null
                    }.await()
                }
            } catch (e: TimeoutCancellationException) {
                throw IOException("Delete timed out", e)   // must not escape as a CancellationException
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
        const val STORAGE_MAX_RETRY_TIME_MILLIS = 10_000L

        const val MAX_BATCH_WRITES = 500

        /** A Firestore `in` query accepts at most 30 values (A-08). */
        const val MAX_IN_VALUES = 30

        const val TAG = "FirebasePostRepository"
    }
}

class PostDeletionCleanupException(val postId: String, cause: Exception) :
    Exception("Post deleted, but related data could not be cleaned up. Retry cleanup.", cause)
