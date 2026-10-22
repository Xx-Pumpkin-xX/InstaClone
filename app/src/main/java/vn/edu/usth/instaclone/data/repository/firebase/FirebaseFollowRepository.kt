package vn.edu.usth.instaclone.data.repository.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.repository.FollowRepository

/**
 * Real FollowRepository on Cloud Firestore (FR-16).
 *
 * users/{me}/following/{them}     createdAt
 * users/{them}/followers/{me}     createdAt
 *
 * follow/unfollow run in a transaction so a repeated tap is idempotent and both
 * counters change by exactly +1 or -1 (NFR-06). The transaction reads every
 * document before writing, as Firestore requires.
 *
 * Writes require Firestore rules for the two follow subcollections and for the
 * counter increments. Until those rules are deployed, reads/writes are denied.
 */
class FirebaseFollowRepository : FollowRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val users = db.collection("users")

    override suspend fun isFollowing(targetUid: String): Boolean {
        val me = auth.currentUser?.uid ?: return false
        if (targetUid.isBlank() || targetUid == me) return false

        return users.document(me)
            .collection("following")
            .document(targetUid)
            .get()
            .await()
            .exists()
    }

    override suspend fun follow(targetUid: String) {
        val me = signedInUid()
        require(targetUid.isNotBlank()) { "Target user is required" }
        require(targetUid != me) { "Cannot follow yourself" }

        val meRef = users.document(me)
        val targetRef = users.document(targetUid)
        val followingRef = meRef.collection("following").document(targetUid)
        val followerRef = targetRef.collection("followers").document(me)

        db.runTransaction { transaction ->
            val meProfile = transaction.get(meRef)
            val targetProfile = transaction.get(targetRef)
            val alreadyFollowing = transaction.get(followingRef).exists()

            if (!meProfile.exists()) throw IllegalStateException("Profile not found")
            if (!targetProfile.exists()) throw IllegalStateException("User not found")
            if (alreadyFollowing) return@runTransaction

            val createdAt = FieldValue.serverTimestamp()
            transaction.set(followingRef, mapOf("createdAt" to createdAt))
            transaction.set(followerRef, mapOf("createdAt" to createdAt))
            transaction.update(meRef, "followingCount", FieldValue.increment(1))
            transaction.update(targetRef, "followerCount", FieldValue.increment(1))
        }.await()
    }

    override suspend fun unfollow(targetUid: String) {
        val me = signedInUid()
        require(targetUid.isNotBlank()) { "Target user is required" }
        require(targetUid != me) { "Cannot unfollow yourself" }

        val meRef = users.document(me)
        val targetRef = users.document(targetUid)
        val followingRef = meRef.collection("following").document(targetUid)
        val followerRef = targetRef.collection("followers").document(me)

        db.runTransaction { transaction ->
            val meProfile = transaction.get(meRef)
            val targetProfile = transaction.get(targetRef)
            val following = transaction.get(followingRef)

            if (!meProfile.exists()) throw IllegalStateException("Profile not found")
            if (!following.exists()) return@runTransaction

            transaction.delete(followingRef)
            transaction.delete(followerRef)
            transaction.update(meRef, "followingCount", FieldValue.increment(-1))
            // The target account may have been deleted; removing the stale follow is still safe.
            if (targetProfile.exists()) {
                transaction.update(targetRef, "followerCount", FieldValue.increment(-1))
            }
        }.await()
    }

    override suspend fun followingIds(): List<String> {
        val me = auth.currentUser?.uid ?: return emptyList()

        return users.document(me)
            .collection("following")
            .limit(MAX_FOLLOWING_IDS)
            .get()
            .await()
            .documents
            .map { it.id }
    }

    private fun signedInUid(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("Must be signed in")
    }

    private companion object {
        /** A Firestore `in` query accepts at most 30 values (A-08). */
        const val MAX_FOLLOWING_IDS = 30L
    }
}
