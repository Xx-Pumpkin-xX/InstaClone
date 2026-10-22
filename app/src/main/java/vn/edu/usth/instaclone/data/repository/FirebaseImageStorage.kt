package vn.edu.usth.instaclone.data.repository

import com.google.firebase.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await

class FirebaseImageStorage : ImageStorage {
    private val storageRef = Firebase.storage.reference

    override suspend fun upload(path: String, bytes: ByteArray): String {
        val ref = storageRef.child(path)
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()
        ref.putBytes(bytes, metadata).await()
        return ref.downloadUrl.await().toString()
    }

    override suspend fun delete(path: String) {
        if (path.isBlank()) return
        try {
            storageRef.child(path).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                throw e
            }
        }
    }
}
