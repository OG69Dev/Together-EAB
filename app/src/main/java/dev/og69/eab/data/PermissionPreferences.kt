package dev.og69.eab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Separate from [SessionRepository] so sign-out does not clear permission UX flags. */
private val Context.permissionUxDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "permission_ux",
)

class PermissionPreferences(context: Context) {

    private val ds = context.applicationContext.permissionUxDataStore

    private object Keys {
        val notificationsAutoPromptDone = booleanPreferencesKey("notifications_auto_prompt_done")
    }

    suspend fun getNotificationsAutoPromptCompleted(): Boolean =
        ds.data.map { it[Keys.notificationsAutoPromptDone] == true }.first()

    suspend fun setNotificationsAutoPromptCompleted(value: Boolean) {
        ds.edit { it[Keys.notificationsAutoPromptDone] = value }
    }
}
