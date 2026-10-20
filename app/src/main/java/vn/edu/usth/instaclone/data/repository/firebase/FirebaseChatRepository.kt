package vn.edu.usth.instaclone.data.repository.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import vn.edu.usth.instaclone.data.model.Conversation
import vn.edu.usth.instaclone.data.model.Message
import vn.edu.usth.instaclone.data.model.User
import vn.edu.usth.instaclone.data.repository.ChatRepository

/**
 * Real ChatRepository on Cloud Firestore (FR-18, FR-19, FR-20).
 *
 * conversations/{uidA_uidB}            participants, participantUsernames, lastMessageText, lastMessageAt, lastSenderId
 * conversations/{uidA_uidB}/messages   senderId, text, createdAt
 *
 * The conversation list query needs a composite index (participants array-contains + lastMessageAt desc).
 * The first time it runs, Logcat prints a link that creates it in one click.
 */
class FirebaseChatRepository : ChatRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val conversationsRef = db.collection("conversations")
    private var cachedMyUsername: String? = null

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

    override fun messages(conversationId: String, limit: Long): Flow<List<Message>> = callbackFlow {
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

        val usernames = buildMap<String, String> {
            myUsername(me).takeIf { it.isNotBlank() }?.let { put(me, it) }
            other.username.takeIf { it.isNotBlank() && it != other.id }?.let { put(other.id, it) }
        }

        // UC-14 step 4: message + conversation summary in ONE batch.
        db.batch()
            .set(messageRef, mapOf(
                "senderId" to me,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp(),
            ))
            .set(conversationRef, mapOf(
                "participants" to listOf(me, other.id).sorted(),
                "participantUsernames" to usernames,
                "lastMessageText" to text,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastSenderId" to me,
            ), SetOptions.merge())
            .commit()
            .await()
    }

    private suspend fun myUsername(me: String): String {
        cachedMyUsername?.let { return it }
        val name = db.collection("users").document(me).get().await().getString("username") ?: ""
        if (name.isNotBlank()) cachedMyUsername = name
        return name
    }
}
