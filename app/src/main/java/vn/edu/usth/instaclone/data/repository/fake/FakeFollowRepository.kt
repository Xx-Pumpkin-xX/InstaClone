package vn.edu.usth.instaclone.data.repository.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.repository.FollowRepository

/**
 * Fake FollowRepository (T-23 support): an in-memory follow graph.
 *
 * FR-14 only reads [isFollowing] to label the Follow/Following button. The write
 * behaviour and its tests belong to FR-16.
 */
class FakeFollowRepository(
    private val myId: () -> String = {
        AppContainer.authRepository.currentUserId.value ?: "me"
    },
    private val initialFollowing: Set<String> = setOf("user-tram", "user-phong"),
) : FollowRepository {

    private val followingByUser = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val seededUsers = mutableSetOf<String>()

    override suspend fun isFollowing(targetUid: String): Boolean {
        return followsOf(myId()).contains(targetUid)
    }

    override suspend fun follow(targetUid: String) {
        val me = myId()
        followingByUser.update { it + (me to (followsOf(me) + targetUid)) }
    }

    override suspend fun unfollow(targetUid: String) {
        val me = myId()
        followingByUser.update { it + (me to (followsOf(me) - targetUid)) }
    }

    override suspend fun followingIds(): List<String> {
        return followsOf(myId()).take(MAX_FOLLOWING_IDS).toList()
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
