package dev.og69.eab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "couples_session"
)

class SessionRepository(context: Context) {

    private val ds = context.applicationContext.sessionDataStore

    private object Keys {
        val coupleId = stringPreferencesKey("couple_id")
        val deviceId = stringPreferencesKey("device_id")
        val deviceToken = stringPreferencesKey("device_token")
        val consentAccepted = booleanPreferencesKey("consent_accepted")
        val profileCompleted = booleanPreferencesKey("profile_completed")
        val profileDisplayName = stringPreferencesKey("profile_display_name")
        val profileShareAll = booleanPreferencesKey("profile_share_all")
        val profileShareBattery = booleanPreferencesKey("profile_share_battery")
        val profileShareStorage = booleanPreferencesKey("profile_share_storage")
        val profileShareCurrentApp = booleanPreferencesKey("profile_share_current_app")
        val profileShareUsage = booleanPreferencesKey("profile_share_usage")
        val profileShareLocation = booleanPreferencesKey("profile_share_location")
        val cachedPartnerJson = stringPreferencesKey("cached_partner_json")
    }

    val sessionFlow: Flow<Session?> = ds.data.map { prefs ->
        val c = prefs[Keys.coupleId]
        val d = prefs[Keys.deviceId]
        val t = prefs[Keys.deviceToken]
        if (!c.isNullOrBlank() && !d.isNullOrBlank() && !t.isNullOrBlank()) {
            Session(c, d, t)
        } else {
            null
        }
    }

    val consentAcceptedFlow: Flow<Boolean> = ds.data.map { it[Keys.consentAccepted] == true }

    val profileCompletedFlow: Flow<Boolean> = ds.data.map { it[Keys.profileCompleted] == true }

    /** Latest partner API JSON from background [dev.og69.eab.work.TelemetryWorker]; cleared on sign-out. */
    val cachedPartnerJsonFlow: Flow<String?> = ds.data.map { it[Keys.cachedPartnerJson] }

    val cachedProfileFlow: Flow<CachedProfile?> = ds.data.map { prefs ->
        if (prefs[Keys.profileCompleted] != true) return@map null
        CachedProfile(
            displayName = prefs[Keys.profileDisplayName].orEmpty(),
            shareAll = prefs[Keys.profileShareAll] == true,
            shareBattery = prefs[Keys.profileShareBattery] == true,
            shareStorage = prefs[Keys.profileShareStorage] == true,
            shareCurrentApp = prefs[Keys.profileShareCurrentApp] == true,
            shareUsage = prefs[Keys.profileShareUsage] == true,
            shareLocation = prefs[Keys.profileShareLocation] == true,
        )
    }

    suspend fun getSession(): Session? = sessionFlow.first()

    suspend fun isProfileCompleted(): Boolean = profileCompletedFlow.first()

    suspend fun saveSession(session: Session) {
        ds.edit { prefs ->
            prefs[Keys.coupleId] = session.coupleId
            prefs[Keys.deviceId] = session.deviceId
            prefs[Keys.deviceToken] = session.deviceToken
        }
    }

    suspend fun setConsentAccepted(value: Boolean) {
        ds.edit { it[Keys.consentAccepted] = value }
    }

    suspend fun saveProfileCache(
        displayName: String,
        shareAll: Boolean,
        shareBattery: Boolean,
        shareStorage: Boolean,
        shareCurrentApp: Boolean,
        shareUsage: Boolean,
        shareLocation: Boolean,
        markCompleted: Boolean,
    ) {
        ds.edit { prefs ->
            prefs[Keys.profileDisplayName] = displayName
            prefs[Keys.profileShareAll] = shareAll
            prefs[Keys.profileShareBattery] = shareBattery
            prefs[Keys.profileShareStorage] = shareStorage
            prefs[Keys.profileShareCurrentApp] = shareCurrentApp
            prefs[Keys.profileShareUsage] = shareUsage
            prefs[Keys.profileShareLocation] = shareLocation
            if (markCompleted) {
                prefs[Keys.profileCompleted] = true
            }
        }
    }

    suspend fun saveCachedPartnerJson(json: String) {
        ds.edit { it[Keys.cachedPartnerJson] = json }
    }

    suspend fun clear() {
        ds.edit { it.clear() }
    }

    data class CachedProfile(
        val displayName: String,
        val shareAll: Boolean,
        val shareBattery: Boolean,
        val shareStorage: Boolean,
        val shareCurrentApp: Boolean,
        val shareUsage: Boolean,
        val shareLocation: Boolean,
    )
}
