package dev.og69.eab.data

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import dev.og69.eab.network.CoupleApi
import java.security.MessageDigest

object ContactsHelper {
    fun getLocalContacts(context: Context): List<CoupleApi.ContactItem> {
        val contacts = mutableSetOf<CoupleApi.ContactItem>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            if (cursor != null) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: ""
                    val rawPhone = cursor.getString(phoneIndex) ?: ""
                    
                    // Simple cleaning for comparisons (optional, but requested simple names/phones)
                    val phone = rawPhone.replace(" ", "").replace("-", "")
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        contacts.add(CoupleApi.ContactItem(name = name.trim(), phone = phone))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return contacts.toList().sortedBy { it.name.lowercase() }
    }

    fun hashContacts(contacts: List<CoupleApi.ContactItem>): String {
        val rawString = contacts.joinToString(";") { "${it.name}:${it.phone}" }
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawString.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
