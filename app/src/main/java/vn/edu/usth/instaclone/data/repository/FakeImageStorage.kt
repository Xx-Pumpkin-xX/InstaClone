package vn.edu.usth.instaclone.data.repository

import kotlinx.coroutines.delay

class FakeImageStorage : ImageStorage {
    private val uploadedPaths = mutableSetOf<String>()

    override suspend fun upload(path: String, bytes: ByteArray): String {
        delay(500) // simulate upload latency
        uploadedPaths.add(path)
        // Return a reliable sample image URL for testing UI previews
        return "https://picsum.photos/1080/1080"
    }

    override suspend fun delete(path: String) {
        delay(300)
        uploadedPaths.remove(path)
    }

    fun isPathUploaded(path: String): Boolean = uploadedPaths.contains(path)
}
