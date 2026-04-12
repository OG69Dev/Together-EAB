package dev.og69.eab.data

import android.content.Context
import dev.og69.eab.network.CoupleApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object WebHistoryHelper {

    private const val MAX_HISTORY_SIZE = 50
    private const val FILE_NAME = "web_history.json"

    suspend fun addUrl(context: Context, url: String, title: String) {
        withContext(Dispatchers.IO) {
            val history = getLocalHistory(context).toMutableList()
            
            val last = history.firstOrNull()
            if (last != null) {
                if (last.url == url) return@withContext
                
                // Debounce typing fragments (incremental additions or backspaces within 5 seconds)
                val timeDiff = System.currentTimeMillis() - last.timestamp
                val isTyping = (url.startsWith(last.url) || last.url.startsWith(url)) && timeDiff < 5000
                if (isTyping) {
                    history[0] = last.copy(
                        url = url,
                        title = title.ifBlank { url },
                        timestamp = System.currentTimeMillis()
                    )
                    saveLocalHistory(context, history)
                    return@withContext
                }
            }

            val item = CoupleApi.WebHistoryItem(
                url = url,
                title = title.ifBlank { url },
                timestamp = System.currentTimeMillis()
            )

            history.add(0, item)
            
            if (history.size > MAX_HISTORY_SIZE) {
                history.subList(MAX_HISTORY_SIZE, history.size).clear()
            }

            saveLocalHistory(context, history)
        }
    }

    suspend fun getLocalHistory(context: Context): List<CoupleApi.WebHistoryItem> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext emptyList()
        try {
            val jsonStr = file.readText()
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<CoupleApi.WebHistoryItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    CoupleApi.WebHistoryItem(
                        url = obj.optString("url"),
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

    private suspend fun saveLocalHistory(context: Context, history: List<CoupleApi.WebHistoryItem>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val arr = JSONArray()
            for (item in history) {
                arr.put(
                    JSONObject()
                        .put("url", item.url)
                        .put("title", item.title)
                        .put("t", item.timestamp)
                )
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // Log or ignore
        }
    }

    fun computeHash(history: List<CoupleApi.WebHistoryItem>): String {
        if (history.isEmpty()) return "empty"
        val sb = StringBuilder()
        for (w in history) {
            sb.append(w.url).append(w.title).append(w.timestamp)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
