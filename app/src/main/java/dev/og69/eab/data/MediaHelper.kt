package dev.og69.eab.data

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * MediaItem represents a piece of media found in the device's storage.
 * @param id The unique MediaStore ID.
 * @param uri The content URI for the item.
 * @param name File name.
 * @param type "image" or "video".
 * @param dateAdded Timestamp when added.
 * @param path File system path if available.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val type: String,
    val dateAdded: Long,
    val path: String
)

class MediaHelper(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Lists all images and videos from MediaStore.
     */
    fun getMediaList(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE
        )

        // Query both images and videos
        val collections = mutableListOf<Uri>()
        collections.add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        collections.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

        for (collection in collections) {
            val cursor = contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val mimeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val path = it.getString(dataColumn)
                    val dateAdded = it.getLong(dateColumn)
                    val mimeType = it.getString(mimeColumn)
                    val type = if (mimeType.contains("video")) "video" else "image"

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = Uri.withAppendedPath(collection, id.toString()),
                            name = name,
                            type = type,
                            dateAdded = dateAdded,
                            path = path
                        )
                    )
                }
            }
        }

        return mediaList.sortedByDescending { it.dateAdded }
    }

    /**
     * Retrieves a thumbnail for a specific media item.
     */
    fun getThumbnail(item: MediaItem, width: Int = 120, height: Int = 120): ByteArray? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(item.uri, Size(width, height), null)
            } else {
                if (item.type == "video") {
                    MediaStore.Video.Thumbnails.getThumbnail(
                        contentResolver,
                        item.id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } else {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        contentResolver,
                        item.id,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null
                    )
                }
            }
            
            val stream = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Flow that emits whenever the MediaStore content changes.
     */
    fun observeMediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }

        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.onStart { emit(Unit) }
}
