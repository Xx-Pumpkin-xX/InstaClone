package vn.edu.usth.instaclone.data.repository

import kotlinx.coroutines.delay

class FakeLikeRepository(
    private val postRepo: FakePostRepository,
) : LikeRepository {

    private val likedIds = mutableSetOf<String>()

    override suspend fun isLiked(postId: String): Boolean = postId in likedIds

    override suspend fun setLiked(postId: String, like: Boolean) {
        delay(300)
        if (like && likedIds.add(postId)) {
            postRepo.addToLikeCount(postId, 1)
        } else if (!like && likedIds.remove(postId)) {
            postRepo.addToLikeCount(postId, -1)
        }
    }
}