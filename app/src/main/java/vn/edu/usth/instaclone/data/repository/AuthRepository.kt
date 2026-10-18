package vn.edu.usth.instaclone.data.repository

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUserId: StateFlow<String?>
    suspend fun signUp(email: String, username: String, password: String)
    suspend fun signIn(email: String, password: String)
    fun SignOut()
    suspend fun sendPasswordReset(email: String)
}