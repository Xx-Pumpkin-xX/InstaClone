package vn.edu.usth.instaclone.data.repository

import com.google.firebase.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirebaseImageStorage : ImageStorage {

    init {
        // Offline, Storage would otherwise keep retrying for minutes.
        // After this limit the task fails, so the UI can show "Couldn't upload the photo. Retry".
        Firebase.storage.maxUploadRetryTimeMillis = STORAGE_MAX_RETRY_TIME_MILLIS
        Firebase.storage.maxOperationRetryTimeMillis = STORAGE_MAX_RETRY_TIME_MILLIS
    }

    private val storageRef = Firebase.storage.reference

    override suspend fun upload(path: String, bytes: ByteArray): String {
        val ref = storageRef.child(path)
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        val task = ref.putBytes(bytes, metadata)
        try {
            task.await()
        } catch (e: CancellationException) {
            // If the ViewModel times out or the screen closes, stop the upload
            // instead of letting it keep running in the background.
            task.cancel()
            throw e
        }
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

    private companion object {
        const val STORAGE_MAX_RETRY_TIME_MILLIS = 10_000L
    }
}
