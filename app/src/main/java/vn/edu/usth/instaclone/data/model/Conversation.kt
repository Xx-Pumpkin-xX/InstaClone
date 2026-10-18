package vn.edu.usth.instaclone.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class Conversation (
    @DocumentId val id: String = "",
    val participants: List<String> = emptyList(),
    val participantUsernames: Map<String, String> =  emptyMap(),
    val lastMessageText: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val lastSenderId: String = "",
) {
    fun otherUserId(myId: String): String = participants.firstOrNull {it != myId} ?: ""
    fun otherUsername(myId: String): String = participantUsernames[otherUserId(myId)] ?: ""
}