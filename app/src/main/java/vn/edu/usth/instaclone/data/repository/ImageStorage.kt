package vn.edu.usth.instaclone.data.repository

interface ImageStorage {
    suspend fun upload(path: String, bytes: ByteArray): String
    suspend fun delete(path: String)
}