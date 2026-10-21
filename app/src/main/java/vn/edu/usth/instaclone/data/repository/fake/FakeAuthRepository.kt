package vn.edu.usth.instaclone.data.repository.fake

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.edu.usth.instaclone.data.repository.AuthRepository

class FakeAuthRepository : AuthRepository {
    private val _currentUserId = MutableStateFlow<String?>(null)
    override val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    var signUpCalls: Int = 0
        private set
    var lastEmail: String? = null
        private set
    var lastUsername: String? = null
        private set
    var signUpError: Exception? = null
    var signUpGate: CompletableDeferred<Unit>? = null

    override suspend fun signUp(email: String, username: String, password: String) {
        signUpCalls++
        lastEmail = email
        lastUsername = username
        // Deliberately do not retain the password in this fake.
        signUpGate?.await()
        signUpError?.let { throw it }
        _currentUserId.value = "fake-user"
    }
    override suspend fun signIn(email: String, password: String) {
        _currentUserId.value = "fake-user"
    }
    override fun signOut() {
        _currentUserId.value = null
    }
    override suspend fun sendPasswordReset(email: String) = Unit
}