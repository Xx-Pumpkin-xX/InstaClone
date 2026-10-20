package vn.edu.usth.instaclone.data.repository

import kotlinx.coroutines.delay

class FakeLikeRepository : LikeRepository {

    // danh sách bài đã like (giả làm server)
    private val danhSachLike = mutableSetOf<String>()

    override suspend fun isLiked(postId: String): Boolean {
        return postId in danhSachLike
    }

    override suspend fun setLiked(postId: String, like: Boolean) {
        delay(300) // giả vờ mạng chậm 0,3 giây
        if (like) {
            danhSachLike.add(postId)
        } else {
            danhSachLike.remove(postId)
        }
    }
}