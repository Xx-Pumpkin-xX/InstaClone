package vn.edu.usth.instaclone.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class Post (
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val imageUrl: String = "",
    val imagePath: String = "",
    val caption: String = "",
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    @ServerTimestamp val createAt: Timestamp? = null,
)