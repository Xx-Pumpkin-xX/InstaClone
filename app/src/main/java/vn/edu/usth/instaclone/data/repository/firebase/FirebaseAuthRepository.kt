package vn.edu.usth.instaclone.data.repository.firebase

import vn.edu.usth.instaclone.data.repository.RegistrationException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.repository.AuthRepository
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class FirebaseAuthRepository : AuthRepository {
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
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

        //Sau khi lấy uid, gọi try và xử lý lỗi ngoài transaction.
        try {
            val claimRef = firestore.collection("usernames").document(name)
            val profileRef = firestore.collection("users").document(uid)

            firestore.runTransaction { transaction ->
                val claim = transaction.get(claimRef)
                if (claim.exists()) {
                    throw RegistrationException.UsernameTaken()
                }
                transaction.set(claimRef, mapOf("uid" to uid))
                transaction.set(
                    profileRef, mapOf(
                        "username" to name,
                        "displayName" to name,
                        "bio" to "",
                        "avatarUrl" to "",
                        "postCount" to 0L,
                        "followerCount" to 0L,
                        "followingCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                Unit
            }.await()

        } catch (cancelled: CancellationException) {
            //Hủy chờ không chứng minh Firebase đã ngừng thao tác.
            //Không xóa Auth khi chưa biết transaction có commit không.
            throw cancelled

        } catch (error: Exception) {
            //SDK có thể bọc lỗi callback transaction trong một exception khác.
            val usernameTaken = generateSequence<Throwable>(error) { it.cause }
                .any { it is RegistrationException.UsernameTaken }

            if (!usernameTaken) {
                //Mốc này chưa phân loại chi tiết các lỗi còn lại.
                //Chặn Home và xác minh/khôi phục, không xóa Auth mù quáng.
                throw RegistrationException.RecoveryRequired(error)
            }

            //Firestore không tạo dở một nửa transaction.
            try {
                withContext(NonCancellable) {
                    withTimeout(10.seconds) {
                        createdUser.delete().await()
                    }
                }
            } catch (cleanupError: Exception) {
                //Không giả vờ rollback thành công nếu xóa Auth thất bại.
                if (cleanupError is CancellationException) {
                    error.addSuppressed(cleanupError)
                }
                throw RegistrationException.RecoveryRequired(error).also {
                    if (cleanupError !is CancellationException) {
                        it.addSuppressed(cleanupError)
                    }
                }
            }

            //Nếu coroutine đã bị hủy trong cleanup, truyền tiếp trạng thái hủy.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            throw RegistrationException.UsernameTaken()
        }
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