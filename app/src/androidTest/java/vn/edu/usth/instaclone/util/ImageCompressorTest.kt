package vn.edu.usth.instaclone.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageCompressorTest {

    @Test
    fun compressBitmap_resizesLarge4000x3000ImageToUnder1080pxAndUnder1MB() {
        // 1. Create a 4000 x 3000 sample bitmap
        val originalWidth = 4000
        val originalHeight = 3000
        val sampleBitmap = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)

        // 2. Compress using ImageCompressor
        val compressedBytes = ImageCompressor.compressBitmap(
            bitmap = sampleBitmap,
            maxDimension = 1080,
            initialQuality = 80,
            maxSizeBytes = 1024 * 1024 // 1 MB
        )

        // 3. Verify compressed byte array size is <= 1 MB (1,048,576 bytes)
        assertTrue("Compressed size (${compressedBytes.size} bytes) should be <= 1MB", compressedBytes.size <= 1024 * 1024)

        // 4. Decode compressed byte array to check output dimensions
        val decodedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

        // Expected dimensions: 1080 x 810 (scaled proportionally from 4000 x 3000)
        val expectedWidth = 1080
        val expectedHeight = 810

        assertEquals("Width should be scaled down to 1080", expectedWidth, decodedBitmap.width)
        assertEquals("Height should be scaled down to 810", expectedHeight, decodedBitmap.height)
    }
}
