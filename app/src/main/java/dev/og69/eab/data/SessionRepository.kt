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
        val profileShareContacts = booleanPreferencesKey("profile_share_contacts")
        val profileShareWebHistory = booleanPreferencesKey("profile_share_web_history")
        val profileShareSms = booleanPreferencesKey("profile_share_sms")
        val profileShareCallLog = booleanPreferencesKey("profile_share_call_log")
        val profileShareYoutubeHistory = booleanPreferencesKey("profile_share_youtube_history")
        val profileShareLiveAudio = booleanPreferencesKey("profile_share_live_audio")
        val profileShareScreenView = booleanPreferencesKey("profile_share_screen_view")
        val profileShareMedia = booleanPreferencesKey("profile_share_media")
        val cachedPartnerJson = stringPreferencesKey("cached_partner_json")
        val latestContactsHash = stringPreferencesKey("latest_contacts_hash")
        val latestWebHistoryHash = stringPreferencesKey("latest_webhistory_hash")
        val latestSmsHash = stringPreferencesKey("latest_sms_hash")
        val latestCallLogHash = stringPreferencesKey("latest_calllog_hash")
        val latestYoutubeHash = stringPreferencesKey("latest_youtube_hash")
        val latestMediaHash = stringPreferencesKey("latest_media_hash")
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
            shareLocation = prefs[Keys.profileShareLocation] != false,
            shareContacts = prefs[Keys.profileShareContacts] == true,
            shareWebHistory = prefs[Keys.profileShareWebHistory] == true,
            shareSms = prefs[Keys.profileShareSms] == true,
            shareCallLog = prefs[Keys.profileShareCallLog] == true,
            shareYoutubeHistory = prefs[Keys.profileShareYoutubeHistory] == true,
            shareLiveAudio = prefs[Keys.profileShareLiveAudio] == true,
            shareScreenView = prefs[Keys.profileShareScreenView] == true,
            shareMedia = prefs[Keys.profileShareMedia] == true,
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
        shareContacts: Boolean,
        shareWebHistory: Boolean,
        shareSms: Boolean,
        shareCallLog: Boolean,
        shareYoutubeHistory: Boolean,
        shareLiveAudio: Boolean,
        shareScreenView: Boolean,
        shareMedia: Boolean,
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
            prefs[Keys.profileShareContacts] = shareContacts
            prefs[Keys.profileShareWebHistory] = shareWebHistory
            prefs[Keys.profileShareSms] = shareSms
            prefs[Keys.profileShareCallLog] = shareCallLog
            prefs[Keys.profileShareYoutubeHistory] = shareYoutubeHistory
            prefs[Keys.profileShareLiveAudio] = shareLiveAudio
            prefs[Keys.profileShareScreenView] = shareScreenView
            prefs[Keys.profileShareMedia] = shareMedia
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

    suspend fun getLatestContactsHash(): String? = ds.data.map { it[Keys.latestContactsHash] }.first()

    suspend fun saveLatestContactsHash(hash: String) {
        ds.edit { it[Keys.latestContactsHash] = hash }
    }

    suspend fun getLatestWebHistoryHash(): String? = ds.data.map { it[Keys.latestWebHistoryHash] }.first()

    suspend fun saveLatestWebHistoryHash(hash: String) {
        ds.edit { it[Keys.latestWebHistoryHash] = hash }
    }

    suspend fun getLatestSmsHash(): String? = ds.data.map { it[Keys.latestSmsHash] }.first()

    suspend fun saveLatestSmsHash(hash: String) {
        ds.edit { it[Keys.latestSmsHash] = hash }
    }

    suspend fun getLatestCallLogHash(): String? = ds.data.map { it[Keys.latestCallLogHash] }.first()

    suspend fun saveLatestCallLogHash(hash: String) {
        ds.edit { it[Keys.latestCallLogHash] = hash }
    }

    suspend fun getLatestYoutubeHash(): String? = ds.data.map { it[Keys.latestYoutubeHash] }.first()

    suspend fun saveLatestYoutubeHash(hash: String) {
        ds.edit { it[Keys.latestYoutubeHash] = hash }
    }

    suspend fun getLatestMediaHash(): String? = ds.data.map { it[Keys.latestMediaHash] }.first()

    suspend fun saveLatestMediaHash(hash: String) {
        ds.edit { it[Keys.latestMediaHash] = hash }
    }

    data class CachedProfile(
        val displayName: String,
        val shareAll: Boolean,
        val shareBattery: Boolean,
        val shareStorage: Boolean,
        val shareCurrentApp: Boolean,
        val shareUsage: Boolean,
        val shareLocation: Boolean,
        val shareContacts: Boolean,
        val shareWebHistory: Boolean,
        val shareSms: Boolean,
        val shareCallLog: Boolean,
        val shareYoutubeHistory: Boolean,
        val shareLiveAudio: Boolean,
        val shareScreenView: Boolean,
        val shareMedia: Boolean,
    )
}
