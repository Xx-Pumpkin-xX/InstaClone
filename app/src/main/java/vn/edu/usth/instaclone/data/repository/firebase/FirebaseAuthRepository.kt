package vn.edu.usth.instaclone.data.repository.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.repository.AuthRepository

class FirebaseAuthRepository : AuthRepository {
    private val auth = Firebase.auth
    private val _currentUserId = MutableStateFlow(auth.currentUser?.uid)
    override  val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUserId.value = firebaseAuth.currentUser?.uid
        }
    }
    override suspend fun signUp(email: String, username: String, password: String) {
        TODO("T-09 . Dat: create the account, claim the username, delete the account if the claim fails")
    }
    override suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }
    override fun signOut() {
        auth.signOut()
    }
    override suspend fun sendPasswordReset(email: String) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
        } catch (e: FirebaseAuthInvalidUserException) {}
    }
}