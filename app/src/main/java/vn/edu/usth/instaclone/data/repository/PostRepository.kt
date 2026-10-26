package vn.edu.usth.instaclone.data.repository

import vn.edu.usth.instaclone.data.model.Post

interface PostRepository {
    /**
     * The 10 (or [limit]) newest posts from everybody, newest first.
     *
     * [after] is an exclusive cursor: returned posts come strictly after it in
     * `(createdAt descending, document ID descending)` order. `null` returns the
     * first page. Posts sharing the cursor's `createdAt` but ordered after it by
     * document ID (descending) are still returned, so a deleted cursor cannot
     * skip them.
     */
    suspend fun feedPage(after: Post? = null, limit: Int = 10): List<Post>

    /**
     * The newest posts written by accounts the signed-in member follows (FR-08).
     *
     * - Newest first by `createdAt`.
     * - [after] is an exclusive cursor, like [feedPage].
     * - The member's own posts are never included.
     * - At most 30 followed accounts are used (A-08), the newest 30 follows.
     * - Returns an empty list when signed out, following nobody, the cursor has
     *   no usable `createdAt`, or `limit <= 0`.
     * - Repository failures propagate; the ViewModel maps them to user copy.
     */
    suspend fun followingFeedPage(after: Post? = null, limit: Int = 10): List<Post>
    suspend fun userPosts(uid: String): List<Post>
    suspend fun getPost(postId: String): Post?
    suspend fun createPost(imageUrl: String, imagePath: String, caption: String): String
    suspend fun createPostWithId(
        postId: String,
        imageUrl: String,
        imagePath: String,
        caption: String
    ): String = createPost(imageUrl, imagePath, caption)
    suspend fun deletePost(postId: String)
    suspend fun retryPendingPostCleanups() = Unit

}

class PostCreationException(
    val commitOutcome: CommitOutcome,
    cause: Throwable
) : Exception("Post creation failed", cause) {
    enum class CommitOutcome {
        NOT_COMMITTED,
        UNKNOWN
    }
}
