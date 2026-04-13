package dev.og69.eab.data

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import dev.og69.eab.network.CoupleApi
import java.security.MessageDigest

object CallLogHelper {

    /** Read the most recent 100 call log entries. */
    fun getLocalCallLog(context: Context): List<CoupleApi.CallLogItem> {
        val list = mutableListOf<CoupleApi.CallLogItem>()
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )
            if (cursor != null) {
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                var count = 0
                while (cursor.moveToNext() && count < 100) {
                    val number = cursor.getString(numIdx) ?: ""
                    val name = cursor.getString(nameIdx) ?: ""
                    val date = cursor.getLong(dateIdx)
                    val duration = cursor.getLong(durIdx)
                    val type = cursor.getInt(typeIdx)
                    val typeStr = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        else -> "other"
                    }
                    if (number.isNotBlank()) {
                        list.add(
                            CoupleApi.CallLogItem(
                                number = number,
                                name = name,
                                timestamp = date,
                                durationSeconds = duration,
                                type = typeStr,
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return list
    }

    fun hashCallLog(items: List<CoupleApi.CallLogItem>): String {
        if (items.isEmpty()) return "empty"
        val sb = StringBuilder()
        for (c in items) sb.append(c.number).append(c.timestamp).append(c.durationSeconds)
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sb.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
