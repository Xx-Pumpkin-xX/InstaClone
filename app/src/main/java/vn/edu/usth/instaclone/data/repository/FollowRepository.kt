package vn.edu.usth.instaclone.data.repository

interface FollowRepository {
    suspend fun isFollowing(targetUid: String): Boolean
    suspend fun follow(targetUid: String)
    suspend fun unfollow(targetUid: String)
    suspend fun followingIds(): List<String>
}