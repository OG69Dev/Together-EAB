package dev.og69.eab.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import dev.og69.eab.network.CoupleApi
import java.security.MessageDigest

object SmsHelper {

    /** Read the most recent 100 SMS messages with contact name resolution. */
    fun getLocalSms(context: Context): List<CoupleApi.SmsItem> {
        val list = mutableListOf<CoupleApi.SmsItem>()
        val nameCache = mutableMapOf<String, String>() // number -> resolved name
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                ),
                null, null,
                "${Telephony.Sms.DATE} DESC"
            )
            if (cursor != null) {
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                var count = 0
                while (cursor.moveToNext() && count < 100) {
                    val address = cursor.getString(addrIdx) ?: ""
                    val body = cursor.getString(bodyIdx) ?: ""
                    val date = cursor.getLong(dateIdx)
                    val type = cursor.getInt(typeIdx)
                    if (address.isNotBlank()) {
                        val contactName = nameCache.getOrPut(address) {
                            resolveContactName(context, address)
                        }
                        list.add(
                            CoupleApi.SmsItem(
                                address = contactName,
                                body = body.take(500),
                                timestamp = date,
                                isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
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

    /** Resolve a phone number to a contact name, or return the raw number if not found. */
    private fun resolveContactName(context: Context, phoneNumber: String): String {
        var contactCursor: Cursor? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            contactCursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            if (contactCursor != null && contactCursor.moveToFirst()) {
                val nameIdx = contactCursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    val name = contactCursor.getString(nameIdx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) {
            // Fall through to return raw number
        } finally {
            contactCursor?.close()
        }
        return phoneNumber
    }

    fun hashSms(items: List<CoupleApi.SmsItem>): String {
        if (items.isEmpty()) return "empty"
        val sb = StringBuilder()
        for (s in items) sb.append(s.address).append(s.body).append(s.timestamp)
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sb.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
