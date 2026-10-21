package vn.edu.usth.instaclone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

object ImageCompressor {

    const val MAX_DIMENSION_PX = 1080
    const val DEFAULT_QUALITY = 80
    const val MAX_FILE_SIZE_BYTES = 1024 * 1024L // 1 MB

    /**
     * Compress an image from a [Uri] (e.g. selected from Photo Picker).
     *
     * 1. Reads orientation EXIF tag to correct image rotation if needed.
     * 2. Resizes bitmap so the longest edge is at most [maxDimension].
     * 3. Encodes to JPEG format at [initialQuality] (default 80%).
     * 4. Strips all EXIF/GPS metadata by re-encoding pixel data into a clean byte stream.
     * 5. Ensures the resulting byte array is <= [maxSizeBytes] (default 1 MB).
     */
    fun compressUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_DIMENSION_PX,
        initialQuality: Int = DEFAULT_QUALITY,
        maxSizeBytes: Long = MAX_FILE_SIZE_BYTES
    ): ByteArray? {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null

        // 1. Determine rotation from EXIF if present
        val rotationAngle = getRotationAngle(context, uri)

        // 2. Decode bitmap from input stream
        val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        inputStream.close()

        // 3. Rotate bitmap if needed
        val rotatedBitmap = if (rotationAngle != 0) {
            rotateBitmap(originalBitmap, rotationAngle)
        } else {
            originalBitmap
        }

        // 4. Compress bitmap (resize, re-encode, strip metadata, enforce size limit)
        return compressBitmap(
            bitmap = rotatedBitmap,
            maxDimension = maxDimension,
            initialQuality = initialQuality,
            maxSizeBytes = maxSizeBytes
        )
    }

    /**
     * Resizes a [Bitmap] to fit within [maxDimension] on its longest edge and compresses it to JPEG.
     * Re-encoding the raw pixel array strips all EXIF/GPS metadata (NFR-04).
     */
    fun compressBitmap(
        bitmap: Bitmap,
        maxDimension: Int = MAX_DIMENSION_PX,
        initialQuality: Int = DEFAULT_QUALITY,
        maxSizeBytes: Long = MAX_FILE_SIZE_BYTES
    ): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val maxEdge = maxOf(width, height)

        // Calculate scaling if image exceeds max dimension
        val scaledBitmap = if (maxEdge > maxDimension) {
            val scale = maxDimension.toFloat() / maxEdge
            val newWidth = (width * scale).roundToInt()
            val newHeight = (height * scale).roundToInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        var quality = initialQuality
        var outputBytes: ByteArray

        // Compress and lower quality iteratively if size exceeds maxSizeBytes
        do {
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputBytes = outputStream.toByteArray()
            quality -= 10
        } while (outputBytes.size > maxSizeBytes && quality >= 10)

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        return outputBytes
    }

    private fun getRotationAngle(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(angle.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) {
            source.recycle()
        }
        return rotated
    }
}
