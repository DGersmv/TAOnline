package com.example.chonline

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaBucket(
    val id: Long?,
    val displayName: String,
    val relativePath: String?
)

data class MediaItem(
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val bucketId: Long?,
    val bucketDisplayName: String?
)

enum class MediaPickerFilter {
    PHOTO,
    PANORAMA
}

object MediaStoreRepository {

    private const val TAG = "MediaStoreRepository"
    private val PANORAMA_PREFIXES = listOf(
        "PANO360_STITCHED_",
        "PANO360_",
        "PANORAMA360_"
    )
    private const val PANORAMA_MIN_ASPECT_RATIO = 2.4f

    suspend fun loadBuckets(context: Context): List<MediaBucket> = withContext(Dispatchers.IO) {
        val buckets = LinkedHashMap<Long, MediaBucket>()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getLong(idColumn)
                    if (!buckets.containsKey(bucketId)) {
                        val displayName = cursor.getString(nameColumn) ?: "Без имени"
                        val relativePath = if (pathColumn >= 0) cursor.getString(pathColumn) else null
                        buckets[bucketId] = MediaBucket(bucketId, displayName, relativePath)
                    }
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "Ошибка чтения buckets: ${error.message}", error)
        }

        buckets.values.sortedBy { it.displayName.lowercase() }
    }

    suspend fun loadMediaItems(
        context: Context,
        bucketId: Long?,
        filter: MediaPickerFilter
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val selection: String?
        val selectionArgs: Array<String>?

        if (bucketId != null) {
            selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
            selectionArgs = arrayOf(bucketId.toString())
        } else {
            selection = null
            selectionArgs = null
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val items = mutableListOf<MediaItem>()
        runCatching {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val item = MediaItem(
                        uri = uri,
                        displayName = cursor.getString(nameColumn) ?: "image_$id",
                        dateAdded = cursor.getLong(dateColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        bucketId = cursor.getLong(bucketIdColumn),
                        bucketDisplayName = cursor.getString(bucketNameColumn)
                    )
                    items.add(item)
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "Ошибка чтения изображений: ${error.message}", error)
        }

        return@withContext when (filter) {
            MediaPickerFilter.PHOTO -> items.filterNot { it.isPanoramaCandidate() }
            MediaPickerFilter.PANORAMA -> items.filter { it.isPanoramaCandidate() }
        }
    }

    fun guessDefaultCameraBucket(buckets: List<MediaBucket>): MediaBucket? {
        if (buckets.isEmpty()) return null
        val cameraPathKeywords = listOf("dcim/camera", "camera")
        return buckets.firstOrNull { bucket ->
            val path = bucket.relativePath?.lowercase()
            val name = bucket.displayName.lowercase()
            cameraPathKeywords.any { keyword ->
                (path?.contains(keyword) == true) || name == keyword.removePrefix("dcim/")
            }
        } ?: buckets.first()
    }

    private fun MediaItem.isPanoramaCandidate(): Boolean {
        val name = displayName.lowercase()
        if (PANORAMA_PREFIXES.any { prefix -> name.startsWith(prefix.lowercase()) }) {
            return true
        }
        val w = width
        val h = height
        if (w > 0 && h > 0 && w >= h) {
            val aspect = w.toFloat() / h.toFloat()
            if (aspect >= PANORAMA_MIN_ASPECT_RATIO) return true
        }
        return false
    }
}

