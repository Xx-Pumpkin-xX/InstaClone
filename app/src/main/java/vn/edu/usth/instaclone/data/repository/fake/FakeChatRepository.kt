package vn.edu.usth.instaclone.data.repository.fake

import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import vn.edu.usth.instaclone.data.AppContainer
import vn.edu.usth.instaclone.data.model.Conversation
import vn.edu.usth.instaclone.data.model.Message
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.ChatRepository
import java.io.IOException
import java.util.Date
import java.util.UUID

/**
 * Fake ChatRepository (T-29, T-30): in-memory data, no Firebase.
 * Lets the chat screens be built and tested before the real backend is plugged in.
 *
 * Testing tips:
 *  - "demo-user" (the profile opened from Home) has 45 messages, enough to test loading older messages.
 *  - Send a message containing "#fail" to test the "Not sent" + Retry state.
 */
class FakeChatRepository(
    private val myId: () -> String = { AppContainer.authRepository.currentUserId.value ?: "me" },
) : ChatRepository {

    private val conversationsFlow = MutableStateFlow<List<Conversation>>(emptyList())
    private val messageFlows = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private var seeded = false

    override fun conversations(): Flow<List<Conversation>> = flow {
        seedIfNeeded()
        emitAll(conversationsFlow.map { list -> list.sortedByDescending { it.lastMessageAt } })
    }

    override fun messages(conversationId: String, limit: Long): Flow<List<Message>> = flow {
        seedIfNeeded()
        emitAll(flowFor(conversationId).map { it.takeLast(limit.toInt()) })
    }

    override suspend fun olderMessages(conversationId: String, before: Message, limit: Long): List<Message> {
        delay(400) // pretend to be the network
        val all = flowFor(conversationId).value
        val index = all.indexOfFirst { it.id == before.id }
        if (index <= 0) return emptyList()
        return all.subList(maxOf(0, index - limit.toInt()), index)
    }

    override suspend fun sendMessage(other: User, text: String) {
        delay(300)
        if (text.contains("#fail")) throw IOException("Fake failure for testing Retry")
        val me = myId()
        val id = conversationId(me, other.id)
        val now = Timestamp.now()
        flowFor(id).update { it + Message(id = UUID.randomUUID().toString(), senderId = me, text = text, createdAt = now) }
        conversationsFlow.update { list ->
            val old = list.firstOrNull { it.id == id }
            val updated = (old ?: Conversation(
                id = id,
                participants = listOf(me, other.id).sorted(),
                participantUsernames = mapOf(me to "you", other.id to other.username.ifBlank { other.id }),
            )).copy(lastMessageText = text, lastMessageAt = now, lastSenderId = me)
            list.filterNot { it.id == id } + updated
        }
    }

    // ---------- demo data ----------

    private fun flowFor(conversationId: String) =
        messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    private fun minutesAgo(minutes: Long) = Timestamp(Date(System.currentTimeMillis() - minutes * 60_000))

    private fun seedIfNeeded() {
        if (seeded) return
        seeded = true
        val me = myId()
        seedConversation(me, "demo-user", "demo.user", count = 45, newestMinutesAgo = 5)
        seedConversation(me, "u-tram", "tram.nguyen", count = 6, newestMinutesAgo = 60 * 26)
        seedConversation(me, "u-nam", "nam.le", count = 3, newestMinutesAgo = 60 * 24 * 9)
    }

    private fun seedConversation(me: String, otherId: String, otherUsername: String, count: Int, newestMinutesAgo: Long) {
        val id = conversationId(me, otherId)
        val lines = listOf("Ê m", "Push code ch?", "Đang sửa lỗi", "Ok", "Làm nhanh còn nộp", "Ok")
        val messages = (0 until count).map { i ->
            Message(
                id = "$id-$i",
                senderId = if (i % 2 == 0) otherId else me,
                text = "${lines[i % lines.size]} (#${i + 1})",
                createdAt = minutesAgo(newestMinutesAgo + (count - 1 - i) * 7L),
            )
        }
        flowFor(id).value = messages
        val last = messages.last()
        conversationsFlow.update {
            it + Conversation(
                id = id,
                participants = listOf(me, otherId).sorted(),
                participantUsernames = mapOf(me to "you", otherId to otherUsername),
                lastMessageText = last.text,
                lastMessageAt = last.createdAt,
                lastSenderId = last.senderId,
            )
        }
    }
}
