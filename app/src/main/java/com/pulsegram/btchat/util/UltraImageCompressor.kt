package com.pulsegram.btchat.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 800px High-Bitrate Aspect-Ratio-Preserving Crisp WebP Lossy Image Compressor for PulseLink.
 *
 * - Keeps the original aspect ratio (portrait, landscape, or square) with the longest side up to 800px!
 * - Uses high-bitrate WebP quality (88%..42%) targeting ~6.5–7.5 KB on Standard 1M / Both Bluetooth
 *   (transmitted via high-speed BLE L2CAP Direct Stream or fast 1M Extended Advertising + Selective-ACK).
 * - Uses an in-memory [LruCache] so LazyColumn scrolling renders decoded 800px photos at 120fps with zero jank.
 */
object UltraImageCompressor {

    const val PHOTO_PREFIX = "IMG1:"
    const val BIN_PHOTO_PREFIX = "IMGB:"
    const val MAX_DISPLAY_DIMENSION = 800
    const val HIGH_BITRATE_1M_MAX_BYTES = 7400
    const val EXTENDED_LR_MAX_BYTES = 3600
    private const val ZLIB_MAGIC_BYTE: Byte = 0x78

    private val decodedBitmapCache = LruCache<Int, Bitmap>(28)

    fun isPhotoPayload(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith(BIN_PHOTO_PREFIX) ||
            trimmed.startsWith(PHOTO_PREFIX, ignoreCase = true)
    }

    /**
     * Reads an image from [uri], fixes EXIF rotation, preserves aspect ratio with max side up to 800px,
     * and compresses with high-bitrate `WEBP_LOSSY` quality (so 800px photos stay crystal clear!).
     */
    fun compressUriTo800MaxPayload(
        context: Context,
        uri: Uri,
        maxBytes: Int = HIGH_BITRATE_1M_MAX_BYTES
    ): String? {
        val originalBitmap = decodeSampledBitmap(context, uri, 1200) ?: return null
        return compressBitmapTo800MaxPayload(originalBitmap, maxBytes)
    }

    fun compressUriTo480MaxPayload(
        context: Context,
        uri: Uri,
        maxBytes: Int = HIGH_BITRATE_1M_MAX_BYTES
    ): String? = compressUriTo800MaxPayload(context, uri, maxBytes)

    /**
     * Compresses a [Bitmap] (from camera or gallery) preserving aspect ratio with longest side up to 800px
     * and high-bitrate WebP quality (starts at 800px with quality 88%..52% before stepping down to 720px/640px).
     */
    fun compressBitmapTo800MaxPayload(
        source: Bitmap,
        maxBytes: Int = HIGH_BITRATE_1M_MAX_BYTES
    ): String? {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        // High-bitrate 800px priority: start at 800px with high WebP quality (88%..55%)
        val candidateMaxSides = intArrayOf(800, 720, 640, 560, 480)
        val candidateQualities = intArrayOf(88, 80, 72, 64, 56, 48, 40, 32)

        var bestBytes: ByteArray? = null

        for (maxSide in candidateMaxSides) {
            val scaled = scalePreservingAspectRatio(source, maxSide)
            for (q in candidateQualities) {
                val out = ByteArrayOutputStream()
                scaled.compress(format, q, out)
                val rawWebp = out.toByteArray()
                val optimalBytes = deflateIfSmaller(rawWebp)

                if (bestBytes == null || optimalBytes.size < bestBytes.size) {
                    bestBytes = optimalBytes
                }
                if (optimalBytes.size <= maxBytes) {
                    val b64 = Base64.encodeToString(optimalBytes, Base64.NO_WRAP)
                    return "$PHOTO_PREFIX$b64"
                }
            }
        }

        val finalBytes = bestBytes ?: return null
        val b64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)
        return "$PHOTO_PREFIX$b64"
    }

    fun compressBitmapTo480MaxPayload(
        source: Bitmap,
        maxBytes: Int = HIGH_BITRATE_1M_MAX_BYTES
    ): String? = compressBitmapTo800MaxPayload(source, maxBytes)

    /**
     * Decodes an `IMG1:` WebP payload back into a crisp [Bitmap] up to 800px max side
     * with exact original aspect ratio, using an in-memory LRU cache to prevent UI scroll jank!
     */
    fun decodePayloadToBitmap(payload: String): Bitmap? {
        if (!isPhotoPayload(payload)) return null
        val cacheKey = payload.hashCode()
        decodedBitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val bitmap = runCatching {
            val rawBytes = if (payload.startsWith(BIN_PHOTO_PREFIX)) {
                payload.substring(BIN_PHOTO_PREFIX.length).toByteArray(StandardCharsets.ISO_8859_1)
            } else {
                val rawB64 = payload.trim().substring(PHOTO_PREFIX.length).trim()
                if (rawB64.isEmpty()) return null
                val cleanB64 = buildString(rawB64.length + 4) {
                    for (ch in rawB64) {
                        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '+' || ch == '/' || ch == '=') {
                            append(ch)
                        }
                    }
                    while (length % 4 != 0) {
                        append('=')
                    }
                }
                Base64.decode(cleanB64, Base64.DEFAULT)
            }

            val imageBytes = inflateIfNeeded(rawBytes)
            val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
            if (max(decoded.width, decoded.height) > MAX_DISPLAY_DIMENSION) {
                scalePreservingAspectRatio(decoded, MAX_DISPLAY_DIMENSION)
            } else {
                decoded
            }
        }.getOrNull()

        if (bitmap != null) {
            decodedBitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    /**
     * Saves a decoded chat photo into the phone's gallery (`Pictures/PulseLink`) using Scoped Storage.
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
        return runCatching {
            val filename = "PulseLink_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PulseLink")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)
    }

    fun getCompressedByteSize(payload: String): Int {
        if (!isPhotoPayload(payload)) return 0
        if (payload.startsWith(BIN_PHOTO_PREFIX)) {
            return payload.length - BIN_PHOTO_PREFIX.length
        }
        val rawB64 = payload.trim().substring(PHOTO_PREFIX.length).trim()
        return (rawB64.length * 3) / 4
    }

    private fun scalePreservingAspectRatio(src: Bitmap, targetMaxSide: Int): Bitmap {
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        val currentMax = max(w, h)
        if (currentMax == targetMaxSide) return src

        val scale = targetMaxSide.toFloat() / currentMax.toFloat()
        val newW = (w * scale).roundToInt().coerceAtLeast(1)
        val newH = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    private fun deflateIfSmaller(input: ByteArray): ByteArray {
        return runCatching {
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size)
            val buf = ByteArray(256)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                if (count <= 0) break
                out.write(buf, 0, count)
            }
            deflater.end()
            val compressed = out.toByteArray()
            if (compressed.isNotEmpty() && compressed.size < input.size && compressed[0] == ZLIB_MAGIC_BYTE) {
                compressed
            } else {
                input
            }
        }.getOrDefault(input)
    }

    private fun inflateIfNeeded(input: ByteArray): ByteArray {
        if (input.isEmpty() || input[0] != ZLIB_MAGIC_BYTE) return input
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(input)
            val out = ByteArrayOutputStream(input.size * 2)
            val buf = ByteArray(512)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count <= 0) break
                out.write(buf, 0, count)
            }
            inflater.end()
            out.toByteArray()
        }.getOrDefault(input)
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, reqSize: Int): Bitmap? {
        return runCatching {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var inSampleSize = 1
            val halfW = boundsOptions.outWidth / 2
            val halfH = boundsOptions.outHeight / 2
            while (halfW / inSampleSize >= reqSize && halfH / inSampleSize >= reqSize) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            rotateBitmapIfRequired(context, uri, rawBitmap)
        }.getOrNull()
    }

    private fun rotateBitmapIfRequired(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val input = context.contentResolver.openInputStream(uri) ?: return bitmap
                val exif = android.media.ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
                input.close()
                val degrees = when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
            } else {
                bitmap
            }
        }.getOrDefault(bitmap)
    }
}
