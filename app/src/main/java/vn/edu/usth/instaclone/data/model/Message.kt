package vn.edu.usth.instaclone.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class Message (
    @DocumentId val id: String = "",
    val senderId: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)