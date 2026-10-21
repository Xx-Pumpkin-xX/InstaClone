package vn.edu.usth.instaclone.data.repository.fake

import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.UserRepository

/**
 * Fake UserRepository (T-23): in-memory users so S-06 can be built and tested without Firebase.
 *
 * The signed-in member gets a demo profile on first read, so the Profile tab is never empty.
 * Search and profile editing are only minimal implementations here; FR-16/FR-17 extend them.
 */
class FakeUserRepository(
    private val myId: () -> String = {
        AppContainer.authRepository.currentUserId.value ?: "me"
    },
    private val latencyMillis: Long = 300,
) : UserRepository {

    private val users = MutableStateFlow(seedUsers())

    override suspend fun claimUsername(uid: String, username: String) {
        waitForNetwork()
        val handle = username.lowercase()
        val taken = users.value.values.any { it.username == handle && it.id != uid }
        if (taken) throw IllegalStateException("Username already taken")

        val existing = users.value[uid]
        val newUser = existing?.copy(username = handle) ?: User(
            id = uid,
            username = handle,
            displayName = handle,
            createdAt = Timestamp.now(),
        )
        users.update { it + (uid to newUser) }
    }

    override suspend fun getUser(uid: String): User? {
        waitForNetwork()
        seedCurrentUserIfNeeded(uid)
        return users.value[uid]
    }

    override suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?) {
        waitForNetwork()
        val uid = myId()
        val current = users.value[uid] ?: throw IllegalStateException("Profile not found")
        users.update {
            it + (uid to current.copy(
                displayName = displayName,
                bio = bio,
                avatarUrl = avatarUrl ?: current.avatarUrl,
            ))
        }
    }

    override suspend fun searchByPrefix(prefix: String, limit: Int): List<User> {
        waitForNetwork()
        val query = prefix.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        return users.value.values
            .filter { it.username.startsWith(query) }
            .sortedBy { it.username }
            .take(limit)
    }

    private fun seedCurrentUserIfNeeded(uid: String) {
        if (uid != myId() || users.value.containsKey(uid)) return
        users.update {
            it + (uid to User(
                id = uid,
                username = "you",
                displayName = "You",
                bio = "This is your demo profile.",
                avatarUrl = "https://picsum.photos/id/64/200/200",
                postCount = 6,
                followerCount = 24,
                followingCount = 12,
                createdAt = Timestamp.now(),
            ))
        }
    }

    private suspend fun waitForNetwork() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    private fun seedUsers(): Map<String, User> {
        val now = Timestamp.now()
        fun user(
            id: String,
            username: String,
            displayName: String,
            bio: String,
            postCount: Long,
            followers: Long,
            following: Long,
        ) = User(
            id = id,
            username = username,
            displayName = displayName,
            bio = bio,
            avatarUrl = "https://picsum.photos/id/${id.hashCode().and(0x7fffffff) % 90 + 10}/200/200",
            postCount = postCount,
            followerCount = followers,
            followingCount = following,
            createdAt = now,
        )

        return listOf(
            user("user-nam", "dev", "Nam", "Android developer.", 6, 120, 34),
            user("user-tram", "tram", "Trâm", "Feed, likes and comments.", 5, 98, 41),
            user("user-trang", "trang", "Trang", "Profiles, follow and search.", 4, 76, 28),
            user("user-phong", "phong", "Phong", "Foundation and login.", 5, 64, 19),
            user("user-dat", "dat", "Đạt", "Register and security rules.", 5, 52, 23),
            user("user-thinh", "thinh", "Thịnh", "Chat and demo.", 5, 47, 31),
        ).associateBy { it.id }
    }
}
