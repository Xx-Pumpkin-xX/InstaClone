package vn.edu.usth.instaclone.data.repository

import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await

class FirebaseImageStorage : ImageStorage {
    private val storageRef = Firebase.storage.reference

    override suspend fun upload(path: String, bytes: ByteArray): String {
        val ref = storageRef.child(path)
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }

    override suspend fun delete(path: String) {
        if (path.isNotEmpty()) {
            try {
                storageRef.child(path).delete().await()
            } catch (e: Exception) {
                // Ignore if file doesn't exist
            }
        }
    }
}
