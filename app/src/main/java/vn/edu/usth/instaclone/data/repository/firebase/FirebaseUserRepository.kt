package vn.edu.usth.instaclone.data.repository.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.UserRepository
import java.util.Locale

class FirebaseUserRepository : UserRepository {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    override suspend fun claimUsername(uid: String, username: String) {
        require(auth.currentUser?.uid == uid) { "Must be signed in to claim a username" }
        val normalizedUsername = username.trim().lowercase(Locale.ROOT)
        require(USERNAME_PATTERN.matches(normalizedUsername)) { "Invalid username" }

        val usernameRef = db.collection("usernames").document(normalizedUsername)
        val userRef = db.collection("users").document(uid)
        db.runTransaction { transaction ->
            check(!transaction.get(usernameRef).exists()) { "Username already taken" }
            check(!transaction.get(userRef).exists()) { "Profile already exists" }
            transaction.set(usernameRef, mapOf("uid" to uid))
            transaction.set(
                userRef,
                mapOf(
                    "username" to normalizedUsername,
                    "displayName" to normalizedUsername,
                    "bio" to "",
                    "avatarUrl" to "",
                    "postCount" to 0L,
                    "followerCount" to 0L,
                    "followingCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
    }

    override suspend fun getUser(uid: String): User? {
        val snapshot = db.collection("users").document(uid).get().await()
        return snapshot.toObject(User::class.java)
    }

    override suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?) {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Must be signed in to update a profile")
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH) { "Display name is too long" }
        require(bio.length <= MAX_BIO_LENGTH) { "Bio is too long" }

        val profileUpdates = mutableMapOf<String, Any>(
            "displayName" to displayName,
            "bio" to bio,
        )
        if (avatarUrl != null) {
            profileUpdates["avatarUrl"] = avatarUrl
        }
        db.collection("users").document(uid).update(profileUpdates).await()

        if (avatarUrl != null) {
            val posts = db.collection("posts")
                .whereEqualTo("authorId", uid)
                .get()
                .await()

            posts.documents.chunked(MAX_BATCH_WRITES).forEach { documents ->
                val batch = db.batch()
                documents.forEach { document ->
                    batch.update(document.reference, "authorAvatarUrl", avatarUrl)
                }
                batch.commit().await()
            }
        }
    }

    override suspend fun searchByPrefix(prefix: String, limit: Int): List<User> {
        val normalizedPrefix = prefix.trim().lowercase(Locale.ROOT)
        if (normalizedPrefix.isEmpty() || limit <= 0) return emptyList()

        val snapshot = db.collection("users")
            .orderBy("username")
            .startAt(normalizedPrefix)
            .endAt("$normalizedPrefix\uf8ff")
            .limit(minOf(limit, MAX_SEARCH_RESULTS).toLong())
            .get()
            .await()
        return snapshot.toObjects(User::class.java)
    }

    private companion object {
        const val MAX_BATCH_WRITES = 500
        const val MAX_DISPLAY_NAME_LENGTH = 30
        const val MAX_BIO_LENGTH = 150
        const val MAX_SEARCH_RESULTS = 20
        val USERNAME_PATTERN = Regex("^[a-z0-9_]{3,20}$")
    }
}
