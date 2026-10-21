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
        //T-09 Dat: create the account, claim the username, delete the account if the claim fails
        val normalizedEmail = email.trim()
        val name = username.trim().lowercase(java.util.Locale.ROOT)
        if (!Regex("^[a-z0-9_]{3,20}$").matches(name)) {
            throw RegistrationException.InvalidUsername()
        }

        //Yêu cầu đang là guest, không ghi đè phiên người đang đăng nhập.
        check(auth.currentUser == null) { "Sign out before registering" }
        val result = auth.createUserWithEmailAndPassword(
            normalizedEmail, password
        ).await()
        val createdUser = checkNotNull(result.user)
        val uid = createdUser.uid

        // Sau khi lấy uid. Gọi trong try và xử lý lỗi ngoài transaction.
        val claimRef = firestore.collection("usernames").document(name)
        val profileRef = firestore.collection("users").document(uid)

        firestore.runTransaction { transaction ->
            val claim = transaction.get(claimRef)
            if (claim.exists()) {
                throw RegistrationException.UsernameTaken()
            }
            transaction.set(claimRef, mapOf("uid" to uid))
            transaction.set(profileRef, mapOf(
                "username" to name,
                "displayName" to name,
                "bio" to "",
                "avatarUrl" to "",
                "postCount" to 0L,
                "followerCount" to 0L,
                "followingCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            ))
            Unit
        }.await()
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