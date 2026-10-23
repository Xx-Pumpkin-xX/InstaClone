package vn.edu.usth.instaclone.data.repository.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.repository.FollowRepository

/**
 * Fake FollowRepository for unit tests and Compose previews.
 *
 * When a [userRepository] is supplied, [follow] and [unfollow] also change both
 * profile counters, matching the Firebase transaction (FR-16).
 */
class FakeFollowRepository(
    private val myId: () -> String = {
        AppContainer.authRepository.currentUserId.value ?: "me"
    },
    private val initialFollowing: Set<String> = setOf("user-tram", "user-phong"),
    private val userRepository: FakeUserRepository? = null,
) : FollowRepository {

    private val followingByUser = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val seededUsers = mutableSetOf<String>()

    override suspend fun isFollowing(targetUid: String): Boolean {
        val me = myId()
        if (targetUid.isBlank() || targetUid == me) return false
        return followsOf(me).contains(targetUid)
    }

    override suspend fun follow(targetUid: String) {
        val me = myId()
        require(targetUid.isNotBlank()) { "Target user is required" }
        require(targetUid != me) { "Cannot follow yourself" }
        if (userRepository != null && userRepository.getUser(targetUid) == null) {
            throw IllegalStateException("User not found")
        }

        val current = followsOf(me)
        if (current.contains(targetUid)) return

        followingByUser.update { it + (me to (current + targetUid)) }
        userRepository?.applyFollowChange(me, targetUid, following = true)
    }

    override suspend fun unfollow(targetUid: String) {
        val me = myId()
        require(targetUid.isNotBlank()) { "Target user is required" }
        require(targetUid != me) { "Cannot unfollow yourself" }

        val current = followsOf(me)
        if (!current.contains(targetUid)) return

        followingByUser.update { it + (me to (current - targetUid)) }
        userRepository?.applyFollowChange(me, targetUid, following = false)
    }

    /**
     * Same contract as [vn.edu.usth.instaclone.data.repository.firebase.FirebaseFollowRepository.followingIds]:
     * most recently followed first, capped at 30 (A-08). Insertion order is the
     * follow order, so the newest entries are at the end of the set.
     */
    override suspend fun followingIds(): List<String> {
        return followsOf(myId())
            .toList()
            .takeLast(MAX_FOLLOWING_IDS)
            .asReversed()
    }

    private fun followsOf(uid: String): Set<String> {
        if (seededUsers.add(uid)) {
            followingByUser.update { it + (uid to initialFollowing) }
        }
        return followingByUser.value[uid].orEmpty()
    }

    private companion object {
        /** A Firestore `in` query accepts at most 30 values (A-08). */
        const val MAX_FOLLOWING_IDS = 30
    }
}
