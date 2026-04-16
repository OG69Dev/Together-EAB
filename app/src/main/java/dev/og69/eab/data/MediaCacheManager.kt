package dev.og69.eab.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Manages local caching of partner media (thumbnails and full files)
 * in the system's cache directory.
 */
class MediaCacheManager(private val context: Context) {

    private val baseCacheDir = File(context.cacheDir, "partner_media")
    private val thumbDir = File(baseCacheDir, "thumbs")
    private val fileDir = File(baseCacheDir, "files")

    init {
        if (!thumbDir.exists()) thumbDir.mkdirs()
        if (!fileDir.exists()) fileDir.mkdirs()
    }

    /**
     * Saves a thumbnail to disk.
     */
    fun saveThumbnail(id: Long, data: ByteArray) {
        val file = File(thumbDir, "thumb_$id.jpg")
        try {
            FileOutputStream(file).use { it.write(data) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves a thumbnail from disk.
     */
    fun getThumbnail(id: Long): ByteArray? {
        val file = File(thumbDir, "thumb_$id.jpg")
        if (!file.exists()) return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves a full file (image or video) to disk.
     */
    fun saveFullFile(id: Long, data: ByteArray) {
        val file = File(fileDir, "file_$id")
        try {
            FileOutputStream(file).use { it.write(data) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves a full file from disk.
     */
    fun getFullFile(id: Long): ByteArray? {
        val file = File(fileDir, "file_$id")
        if (!file.exists()) return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the local file object for a media item.
     * Useful for video playback via URIs.
     */
    fun getFile(id: Long): File? {
        val file = File(fileDir, "file_$id")
        return if (file.exists()) file else null
    }

    /**
     * Checks if a thumbnail is cached.
     */
    fun hasThumbnail(id: Long) = File(thumbDir, "thumb_$id.jpg").exists()

    /**
     * Checks if a full file is cached.
     */
    fun hasFullFile(id: Long) = File(fileDir, "file_$id").exists()
}
