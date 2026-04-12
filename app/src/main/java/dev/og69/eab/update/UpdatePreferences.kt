package dev.og69.eab.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_updates"
)

class UpdatePreferences(context: Context) {

    private val ds = context.applicationContext.updateDataStore

    private object Keys {
        val lastNotifiedTag = stringPreferencesKey("last_notified_tag")
    }

    suspend fun getLastNotifiedTag(): String? =
        ds.data.map { it[Keys.lastNotifiedTag] }.first()

    suspend fun setLastNotifiedTag(tag: String) {
        ds.edit { it[Keys.lastNotifiedTag] = tag }
    }
}
