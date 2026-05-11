package dev.og69.eab.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object WallpaperHelper {
    private const val TAG = "WallpaperHelper"

    /**
     * Check whether the app can read the wallpaper.
     * On Android 11+ (API 30+), WallpaperManager.getDrawable() requires
     * MANAGE_EXTERNAL_STORAGE because Google removed wallpaper reading
     * access from regular storage permissions for third-party apps.
     */
    fun canReadWallpaper(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Older devices just need READ_EXTERNAL_STORAGE (already declared)
            true
        }
    }

    /**
     * Extracts the current wallpaper as a JPEG byte array.
     * @param target "home" or "lock"
     */
    suspend fun getCurrentWallpaper(context: Context, target: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!canReadWallpaper(context)) {
            Log.w(TAG, "Cannot read wallpaper – MANAGE_EXTERNAL_STORAGE not granted")
            return@withContext null
        }

        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val flag = if (target == "lock") WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM

            val drawable: Drawable? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.getDrawable(flag)
            } else {
                wallpaperManager.drawable
            }

            if (drawable == null) {
                Log.w(TAG, "Wallpaper drawable is null for target $target")
                return@withContext null
            }

            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    // Create a bitmap from the drawable
                    val bmp = Bitmap.createBitmap(
                        drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080,
                        drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            }

            val stream = ByteArrayOutputStream()
            // Compress heavily to ensure fast transfers (target < 2MB)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val bytes = stream.toByteArray()
            
            // Cleanup bitmap if it was newly created
            if (drawable !is BitmapDrawable) {
                bitmap.recycle()
            }
            
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get wallpaper for target $target", e)
            null
        }
    }

    /**
     * Sets the wallpaper from a byte array.
     * @param target "home" or "lock"
     */
    suspend fun setWallpaper(context: Context, target: String, imageBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode wallpaper bitmap")
                return@withContext false
            }

            val wallpaperManager = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val flag = if (target == "lock") WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
                wallpaperManager.setBitmap(bitmap, null, true, flag)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }
            
            bitmap.recycle()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set wallpaper for target $target", e)
            false
        }
    }
}
