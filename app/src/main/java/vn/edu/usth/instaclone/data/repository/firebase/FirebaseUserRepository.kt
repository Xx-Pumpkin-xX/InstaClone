package vn.edu.usth.instaclone.data.repository.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.UserRepository
import java.util.Locale

/**
 * Real UserRepository on Cloud Firestore.
 *
 * users/{uid}            username, displayName, bio, avatarUrl, counts, createdAt
 * usernames/{username}   uid
 *
 * Reads use the default Firestore source, so documents already in the local cache
 * remain readable while the device is offline (NFR-09).
 *
 * Registration already creates both documents in FirebaseAuthRepository.signUp().
 * [claimUsername] is kept for the interface and only creates a missing profile.
 */
class FirebaseUserRepository : UserRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val users = db.collection("users")
    private val usernames = db.collection("usernames")

    override suspend fun claimUsername(uid: String, username: String) {
        val signedInUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Must be signed in to claim a username")
        require(uid == signedInUid) { "Can only claim your own username" }

        val name = username.trim().lowercase(Locale.ROOT)
        require(name.isNotBlank()) { "Username is required" }

        val profileRef = users.document(uid)
        val claimRef = usernames.document(name)

        // Atomic: either both the username claim and the profile exist, or neither does.
        db.runTransaction { transaction ->
            val claim = transaction.get(claimRef)
            val profile = transaction.get(profileRef)

            if (claim.exists()) {
                val alreadyMine = claim.getString("uid") == uid
                    && profile.getString("username") == name
                if (alreadyMine) return@runTransaction
                throw IllegalStateException("Username already taken")
            }
            if (profile.exists()) throw IllegalStateException("Profile already exists")

            transaction.set(claimRef, mapOf("uid" to uid))
            transaction.set(
                profileRef,
                mapOf(
                    "username" to name,
                    "displayName" to name,
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
        if (uid.isBlank()) return null
        val document = users.document(uid).get().await()
        return if (document.exists()) document.toObject(User::class.java) else null
    }

    override suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?) {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Must be signed in to update a profile")

        val updates = buildMap<String, Any> {
            put("displayName", displayName)
            put("bio", bio)
            // null means "keep the current avatar"; the profile rules only allow these keys.
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
        }
        users.document(uid).update(updates).await()
    }

    override suspend fun searchByPrefix(prefix: String, limit: Int): List<User> {
        if (limit <= 0) return emptyList()
        val query = prefix.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return emptyList()

        val cappedLimit = limit.coerceAtMost(MAX_SEARCH_RESULTS)
        val snapshot = users
            .whereGreaterThanOrEqualTo("username", query)
            .whereLessThanOrEqualTo("username", "$query\uF8FF")
            .orderBy("username")
            .limit(cappedLimit.toLong())
            .get()
            .await()

        return snapshot.toObjects(User::class.java)
    }

    private companion object {
        /** FR-17 shows at most 20 matching members. */
        const val MAX_SEARCH_RESULTS = 20
    }
}
