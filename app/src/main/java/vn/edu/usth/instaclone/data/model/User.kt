package vn.edu.usth.instaclone.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class User (
    @DocumentId val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val postCount: Long = 0,
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
)