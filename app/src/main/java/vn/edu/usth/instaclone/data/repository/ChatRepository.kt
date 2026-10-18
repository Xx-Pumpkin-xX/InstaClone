package vn.edu.usth.instaclone.data.repository

import kotlinx.coroutines.flow.Flow
import vn.edu.usth.instaclone.data.model.Conversation
import vn.edu.usth.instaclone.data.model.Message
import vn.edu.usth.instaclone.data.model.User

interface ChatRepository {
    fun conversations(): Flow<List<Conversation>>
    fun messages(conversationId: String, limit: Long = 30): Flow<List<Message>>
    suspend fun olderMessages(conversationId: String, before: Message, limit: Long = 30): List<Message>
    suspend fun sendMessage(other: User, text: String)
    fun conversationId(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")
}