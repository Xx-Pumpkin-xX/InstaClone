package vn.edu.usth.instaclone.data.repository.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.model.Conversation
import vn.edu.usth.instaclone.data.model.Message
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.ChatRepository

/**
 * Real ChatRepository on Cloud Firestore (FR-18, FR-19, FR-20), written to pass firestore.rules (T-08).
 *
 * conversations/{uidA_uidB}            participants, participantUsernames, lastMessageText, lastMessageAt,
 *                                      lastSenderId, lastMessageId
 * conversations/{uidA_uidB}/messages   senderId, text, createdAt
 *
 * The conversation list query needs a composite index (participants array-contains + lastMessageAt desc).
 * The first time it runs, Logcat prints a link that creates it in one click.
 */
class FirebaseChatRepository : ChatRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val conversationsRef = db.collection("conversations")
    private val usernameCache = mutableMapOf<String, String>()

    private fun myId(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")

    override fun conversations(): Flow<List<Conversation>> = callbackFlow {
        val registration = conversationsRef
            .whereArrayContains("participants", myId())
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Conversation::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /**
     * The rules only let participants read messages, and they check that through the conversation
     * document. Before the first message that document does not exist, so listening to the messages
     * right away would be denied. We wait until the conversation appears in our own list, then listen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun messages(conversationId: String, limit: Long): Flow<List<Message>> =
        conversations()
            .map { list -> list.any { it.id == conversationId } }
            .distinctUntilChanged()
            .flatMapLatest { exists -> if (exists) liveMessages(conversationId, limit) else flowOf(emptyList()) }

    private fun liveMessages(conversationId: String, limit: Long): Flow<List<Message>> = callbackFlow {
        val registration = conversationsRef.document(conversationId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                // Newest first from the query; the screen wants oldest first.
                trySend(snapshot?.toObjects(Message::class.java)?.reversed() ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun olderMessages(conversationId: String, before: Message, limit: Long): List<Message> {
        val beforeTime = before.createdAt ?: return emptyList()
        val snapshot = conversationsRef.document(conversationId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(beforeTime)
            .limit(limit)
            .get()
            .await()
        return snapshot.toObjects(Message::class.java).reversed()
    }

    override suspend fun sendMessage(other: User, text: String) {
        val me = myId()
        val conversationRef = conversationsRef.document(conversationId(me, other.id))
        val messageRef = conversationRef.collection("messages").document()

        // The rules require BOTH usernames, exactly as stored in users/{uid}.
        val myName = username(me)
        val otherName = other.username.takeIf { it.isNotBlank() && it != other.id } ?: username(other.id)
        check(myName.isNotBlank() && otherName.isNotBlank()) { "Username not found" }

        // UC-14 step 4: message + conversation summary in ONE batch.
        db.batch()
            .set(messageRef, mapOf(
                "senderId" to me,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp(),
            ))
            .set(conversationRef, mapOf(
                "participants" to listOf(me, other.id).sorted(),
                "participantUsernames" to mapOf(me to myName, other.id to otherName),
                "lastMessageText" to text,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastSenderId" to me,
                "lastMessageId" to messageRef.id,
            ))
            .commit()
            .await()
    }

    private suspend fun username(uid: String): String {
        usernameCache[uid]?.let { return it }
        val name = db.collection("users").document(uid).get().await().getString("username") ?: ""
        if (name.isNotBlank()) usernameCache[uid] = name
        return name
    }
}
