package vn.edu.usth.instaclone.data.repository

import vn.edu.usth.instaclone.data.model.User

interface UserRepository {
    suspend fun claimUsername(uid: String, username: String)
    suspend fun getUser(uid: String): User?
    suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?)
    suspend fun searchByPrefix(prefix: String, limit: Int = 20): List<User>
}