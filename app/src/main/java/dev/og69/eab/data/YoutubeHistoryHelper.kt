package dev.og69.eab.data

import android.content.Context
import dev.og69.eab.network.CoupleApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object YoutubeHistoryHelper {

    private const val MAX_HISTORY_SIZE = 50
    private const val FILE_NAME = "youtube_history.json"

    suspend fun addVideo(context: Context, title: String) {
        withContext(Dispatchers.IO) {
            val history = getLocalHistory(context).toMutableList()
            
            val last = history.firstOrNull()
            if (last != null) {
                if (last.title == title) return@withContext
                
                // Debounce rapid changes (e.g. within 3 seconds)
                val timeDiff = System.currentTimeMillis() - last.timestamp
                if (timeDiff < 3000) {
                    // Update the last entry rather than adding a new one
                    history[0] = last.copy(
                        title = title,
                        timestamp = System.currentTimeMillis()
                    )
                    saveLocalHistory(context, history)
                    return@withContext
                }
            }

            val item = CoupleApi.YoutubeHistoryItem(
                title = title,
                timestamp = System.currentTimeMillis()
            )

            history.add(0, item)
            
            if (history.size > MAX_HISTORY_SIZE) {
                history.subList(MAX_HISTORY_SIZE, history.size).clear()
            }

            saveLocalHistory(context, history)
        }
    }

    suspend fun getLocalHistory(context: Context): List<CoupleApi.YoutubeHistoryItem> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext emptyList()
        try {
            val jsonStr = file.readText()
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<CoupleApi.YoutubeHistoryItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    CoupleApi.YoutubeHistoryItem(
                        title = obj.optString("title"),
                        timestamp = obj.optLong("t", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveLocalHistory(context: Context, history: List<CoupleApi.YoutubeHistoryItem>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val arr = JSONArray()
            for (item in history) {
                arr.put(
                    JSONObject()
                        .put("title", item.title)
                        .put("t", item.timestamp)
                )
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // Log or ignore
        }
    }

    fun computeHash(history: List<CoupleApi.YoutubeHistoryItem>): String {
        if (history.isEmpty()) return "empty"
        val sb = StringBuilder()
        for (w in history) {
            sb.append(w.title).append(w.timestamp)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
