package dev.og69.eab.network

import dev.og69.eab.ApiConfig
import dev.og69.eab.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class IceServerConfig(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)


class CoupleApi(
    private val client: OkHttpClient = defaultClient(),
) {

    private fun baseUrl(): String {
        val u = ApiConfig.WORKER_BASE_URL.trim().trimEnd('/')
        require(!u.contains("YOUR_SUBDOMAIN")) { "Set ApiConfig.WORKER_BASE_URL to your deployed Worker URL" }
        return u
    }

    suspend fun createCouple(): CreateCoupleResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/create"
        val req = Request.Builder().url(url).post("{}".toRequestBody(JSON)).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val j = JSONObject(body)
            CreateCoupleResponse(
                coupleId = j.getString("coupleId"),
                coupleCode = j.getString("coupleCode"),
                deviceId = j.getString("deviceId"),
                deviceToken = j.getString("deviceToken"),
            )
        }
    }

    suspend fun joinCouple(code: String): JoinCoupleResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/join"
        val payload = JSONObject().put("code", code).toString()
        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val j = JSONObject(body)
            JoinCoupleResponse(
                coupleId = j.getString("coupleId"),
                deviceId = j.getString("deviceId"),
                deviceToken = j.getString("deviceToken"),
            )
        }
    }

    suspend fun getIceServers(session: Session): List<IceServerConfig> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/rtc/ice-servers"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .addHeader("X-Device-Id", session.deviceId)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val array = JSONArray(body)
            val list = mutableListOf<IceServerConfig>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val urls = mutableListOf<String>()
                val u = obj.get("urls")
                if (u is JSONArray) {
                    for (j in 0 until u.length()) urls.add(u.getString(j))
                } else {
                    urls.add(u.toString())
                }
                list.add(IceServerConfig(
                    urls = urls,
                    username = obj.optString("username").takeIf { obj.has("username") },
                    credential = obj.optString("credential").takeIf { obj.has("credential") }
                ))
            }
            list
        }
    }

    suspend fun postTelemetry(session: Session, payload: JSONObject) = withContext(Dispatchers.IO) {

        val url = "${baseUrl()}/api/couple/${session.coupleId}/telemetry"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val b = resp.body?.string().orEmpty()
                throw ApiException(resp.code, b)
            }
        }
    }

    suspend fun postProfile(
        session: Session,
        displayName: String,
        shareAll: Boolean,
        shareBattery: Boolean,
        shareStorage: Boolean,
        shareCurrentApp: Boolean,
        shareUsage: Boolean,
        shareLocation: Boolean,
        shareContacts: Boolean,
        shareWebHistory: Boolean,
        shareYoutubeHistory: Boolean,
        shareSms: Boolean,
        shareCallLog: Boolean,
        shareLiveAudio: Boolean,
        shareLiveCamera: Boolean,
        shareScreenView: Boolean,
        shareMedia: Boolean,
        shareAppControl: Boolean,
    ) = withContext(Dispatchers.IO) {

        val url = "${baseUrl()}/api/couple/${session.coupleId}/profile"
        val body = JSONObject()
            .put("displayName", displayName)
            .put("shareAll", shareAll)
            .put("battery", shareBattery)
            .put("storage", shareStorage)
            .put("currentApp", shareCurrentApp)
            .put("usageStats", shareUsage)
            .put("shareLocation", shareLocation)
            .put("shareContacts", shareContacts)
            .put("shareWebHistory", shareWebHistory)
            .put("shareYoutubeHistory", shareYoutubeHistory)
            .put("shareSms", shareSms)
            .put("shareCallLog", shareCallLog)
            .put("shareLiveAudio", shareLiveAudio)
            .put("shareLiveCamera", shareLiveCamera)
            .put("shareScreenView", shareScreenView)
            .put("shareMedia", shareMedia)
            .put("shareAppControl", shareAppControl)
            .toString()

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .post(body.toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val b = resp.body?.string().orEmpty()
                throw ApiException(resp.code, b)
            }
        }
    }

    suspend fun postContacts(session: Session, contacts: List<ContactItem>) = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/contacts"
        val arr = JSONArray()
        for (c in contacts) {
            arr.put(JSONObject().put("name", c.name).put("phone", c.phone))
        }
        val payload = JSONObject().put("contacts", arr)
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val b = resp.body?.string().orEmpty()
                throw ApiException(resp.code, b)
            }
        }
    }

    suspend fun getPartnerContacts(session: Session): List<ContactItem> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/contacts"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val j = JSONObject(body)
            val arr = j.optJSONArray("contacts")
            val list = mutableListOf<ContactItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(ContactItem(o.optString("name", ""), o.optString("phone", "")))
                }
            }
            list
        }
    }

    suspend fun postWebHistory(session: Session, history: List<WebHistoryItem>) = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/webhistory"
        val arr = JSONArray()
        for (w in history) {
            arr.put(JSONObject().put("url", w.url).put("title", w.title).put("t", w.timestamp))
        }
        val payload = JSONObject().put("history", arr)
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val b = resp.body?.string().orEmpty()
                throw ApiException(resp.code, b)
            }
        }
    }

    suspend fun getPartnerWebHistory(session: Session): List<WebHistoryItem> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/webhistory"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val j = JSONObject(body)
            val arr = j.optJSONArray("history")
            val list = mutableListOf<WebHistoryItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(WebHistoryItem(o.optString("url", ""), o.optString("title", ""), o.optLong("t", 0L)))
                }
            }
            list
        }
    }

    // ── YouTube History ──────────────────────────────────────

    suspend fun postYoutubeHistory(session: Session, history: List<YoutubeHistoryItem>) = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/youtube"
        val arr = JSONArray()
        for (w in history) {
            arr.put(JSONObject().put("title", w.title).put("t", w.timestamp))
        }
        val payload = JSONObject().put("history", arr)
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val b = resp.body?.string().orEmpty()
                throw ApiException(resp.code, b)
            }
        }
    }

    suspend fun getPartnerYoutubeHistory(session: Session): List<YoutubeHistoryItem> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/youtube"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val j = JSONObject(body)
            val arr = j.optJSONArray("history")
            val list = mutableListOf<YoutubeHistoryItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(YoutubeHistoryItem(o.optString("title", ""), o.optLong("t", 0L)))
                }
            }
            list
        }
    }

    // ── SMS History ──────────────────────────────────────────

    suspend fun postSmsHistory(session: Session, sms: List<SmsItem>) = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/sms"
        val arr = JSONArray()
        for (s in sms) {
            arr.put(JSONObject()
                .put("address", s.address)
                .put("body", s.body)
                .put("t", s.timestamp)
                .put("incoming", s.isIncoming))
        }
        val payload = JSONObject().put("sms", arr)
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, resp.body?.string().orEmpty())
        }
    }

    suspend fun getPartnerSms(session: Session): List<SmsItem> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/sms"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val arr = JSONObject(body).optJSONArray("sms")
            val list = mutableListOf<SmsItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(SmsItem(
                        address = o.optString("address", ""),
                        body = o.optString("body", ""),
                        timestamp = o.optLong("t", 0L),
                        isIncoming = o.optBoolean("incoming", true),
                    ))
                }
            }
            list
        }
    }

    // ── Call Log ─────────────────────────────────────────────

    suspend fun postCallLog(session: Session, calls: List<CallLogItem>) = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/calllog"
        val arr = JSONArray()
        for (c in calls) {
            arr.put(JSONObject()
                .put("number", c.number)
                .put("name", c.name)
                .put("t", c.timestamp)
                .put("duration", c.durationSeconds)
                .put("type", c.type))
        }
        val payload = JSONObject().put("calls", arr)
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, resp.body?.string().orEmpty())
        }
    }

    suspend fun getPartnerCallLog(session: Session): List<CallLogItem> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/calllog"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val arr = JSONObject(body).optJSONArray("calls")
            val list = mutableListOf<CallLogItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(CallLogItem(
                        number = o.optString("number", ""),
                        name = o.optString("name", ""),
                        timestamp = o.optLong("t", 0L),
                        durationSeconds = o.optLong("duration", 0L),
                        type = o.optString("type", "other"),
                    ))
                }
            }
            list
        }
    }

    // ── App Control ──────────────────────────────────────────

    suspend fun postInstalledApps(session: Session, apps: List<InstalledAppItem>) = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/installed-apps"
        val arr = JSONArray()
        for (a in apps) {
            arr.put(JSONObject().put("packageName", a.packageName).put("label", a.label).put("lastUsed", a.lastUsed))
        }
        val payload = JSONObject().put("apps", arr)
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, resp.body?.string().orEmpty())
        }
    }

    suspend fun getPartnerInstalledApps(session: Session): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/installed-apps"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val arr = JSONObject(body).optJSONArray("apps")
            val list = mutableListOf<InstalledAppItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(InstalledAppItem(
                        packageName = o.optString("packageName", ""),
                        label = o.optString("label", ""),
                        lastUsed = o.optLong("lastUsed", 0L),
                    ))
                }
            }
            list
        }
    }

    suspend fun postAppControl(session: Session, blockedPackages: List<String>, uninstallBlocked: Boolean) = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/app-control"
        val payload = JSONObject()
            .put("blockedPackages", JSONArray(blockedPackages))
            .put("uninstallBlocked", uninstallBlocked)
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, resp.body?.string().orEmpty())
        }
    }

    suspend fun getSelfAppControl(session: Session): AppControlResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/app-control"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            val j = JSONObject(body)
            val arr = j.optJSONArray("blockedPackages")
            val blocked = mutableListOf<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    blocked.add(arr.optString(i))
                }
            }
            AppControlResponse(
                blockedPackages = blocked,
                uninstallBlocked = j.optBoolean("uninstallBlocked", false)
            )
        }
    }

    data class AppControlResponse(
        val blockedPackages: List<String>,
        val uninstallBlocked: Boolean
    )

    suspend fun getPartner(session: Session): PartnerResponse = withContext(Dispatchers.IO) {

        parsePartnerResponse(JSONObject(getPartnerBodyInternal(session)))
    }

    /** Raw `/partner` response body for caching (same payload as [getPartner]). */
    suspend fun getPartnerJson(session: Session): String = withContext(Dispatchers.IO) {
        getPartnerBodyInternal(session)
    }

    private fun getPartnerBodyInternal(session: Session): String {
        val url = "${baseUrl()}/api/couple/${session.coupleId}/partner"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.deviceToken}")
            .get()
            .build()
        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            body
        }
    }

    fun parsePartnerResponse(j: JSONObject): PartnerResponse {
        val linked = j.optBoolean("linked", false)
        val tel = j.optJSONObject("telemetry")
        return PartnerResponse(
            linked = linked,
            partnerName = jsonCleanString(j, "partnerName"),
            partnerSharing = parsePartnerSharing(j),
            telemetry = tel?.let { parseTelemetry(it) },
            appControl = j.optJSONObject("appControl")?.let { 
                val arr = it.optJSONArray("blockedPackages")
                val blocked = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) blocked.add(arr.optString(i))
                }
                AppControlResponse(blocked, it.optBoolean("uninstallBlocked", false))
            }
        )
    }


    private fun parseTelemetry(j: JSONObject): PartnerTelemetry {
        val usage = mutableListOf<UsageStatItem>()
        val arr = j.optJSONArray("usageStats")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                usage.add(
                    UsageStatItem(
                        packageName = o.optString("package", ""),
                        label = o.optString("label", ""),
                        ms = o.optLong("ms", 0L),
                    )
                )
            }
        }
        val locObj = j.optJSONObject("location")
        val locationData = if (locObj != null) {
            LocationData(
                lat = locObj.optDouble("lat", 0.0),
                lng = locObj.optDouble("lng", 0.0),
                acc = locObj.optDouble("acc", 0.0).toFloat(),
                t = locObj.optLong("t", 0L),
            )
        } else null

        return PartnerTelemetry(
            t = j.optLong("t", 0L),
            batteryPct = j.optInt("batteryPct", 0),
            diskFreeBytes = j.optLong("diskFreeBytes", 0L),
            diskTotalBytes = j.optLong("diskTotalBytes", 0L),
            foregroundPackage = jsonCleanString(j, "foregroundPackage"),
            foregroundAppLabel = jsonCleanString(j, "foregroundAppLabel"),
            usageStats = usage,
            usageTodayTotalMs = j.optLong("usageTodayTotalMs", 0L),
            usageWeekTotalMs = j.optLong("usageWeekTotalMs", 0L),
            usageDailyAvgMs = j.optLong("usageDailyAvgMs", 0L),
            location = locationData,
            networkType = j.optString("networkType", "None"),
            networkBars = j.optInt("networkBars", 0),
            networkMaxBars = j.optInt("networkMaxBars", 4),
        )
    }

    private fun parsePartnerSharing(j: JSONObject): PartnerSharing? {
        if (!j.has("partnerSharing") || j.isNull("partnerSharing")) return null
        val o = j.optJSONObject("partnerSharing") ?: return null
        val shareAll = o.optBoolean("shareAll", true)
        val arr = o.optJSONArray("hidden") ?: return PartnerSharing(shareAll = shareAll, hidden = emptyList(), shareLocation = shareAll, shareContacts = shareAll, shareWebHistory = shareAll, shareYoutubeHistory = shareAll, shareLiveAudio = shareAll, shareLiveCamera = shareAll, shareScreenView = shareAll, shareAppControl = shareAll, shareSms = shareAll, shareCallLog = shareAll, shareMedia = shareAll)
        val hidden = buildList {

            for (i in 0 until arr.length()) {
                val k = arr.optString(i, "").trim()
                if (k.isNotEmpty()) add(k)
            }
        }
        val shareLocation = shareAll || !hidden.contains("location")
        val shareContacts = shareAll || !hidden.contains("contacts")
        val shareWebHistory = shareAll || !hidden.contains("webHistory")
        val shareYoutubeHistory = shareAll || !hidden.contains("youtubeHistory")
        val shareSms = shareAll || !hidden.contains("sms")
        val shareCallLog = shareAll || !hidden.contains("callLog")
        val shareLiveAudio = shareAll || !hidden.contains("liveAudio")
        val shareLiveCamera = shareAll || !hidden.contains("liveCamera")
        val shareScreenView = shareAll || !hidden.contains("shareScreenView")
        val shareMedia = shareAll || !hidden.contains("media")
        val shareAppControl = shareAll || !hidden.contains("appControl")
        return PartnerSharing(shareAll = shareAll, hidden = hidden, shareLocation = shareLocation, shareContacts = shareContacts, shareWebHistory = shareWebHistory, shareYoutubeHistory = shareYoutubeHistory, shareLiveAudio = shareLiveAudio, shareLiveCamera = shareLiveCamera, shareScreenView = shareScreenView, shareAppControl = shareAppControl, shareSms = shareSms, shareCallLog = shareCallLog, shareMedia = shareMedia)
    }


    private fun jsonCleanString(j: JSONObject, key: String): String? {
        if (!j.has(key) || j.isNull(key)) return null
        val s = j.optString(key, "").trim()
        if (s.isEmpty() || s.equals("null", ignoreCase = true)) return null
        return s
    }

    data class CreateCoupleResponse(
        val coupleId: String,
        val coupleCode: String,
        val deviceId: String,
        val deviceToken: String,
    )

    data class JoinCoupleResponse(
        val coupleId: String,
        val deviceId: String,
        val deviceToken: String,
    )

    data class PartnerSharing(
        val shareAll: Boolean,
        val hidden: List<String>,
        val shareLocation: Boolean,
        val shareContacts: Boolean,
        val shareWebHistory: Boolean,
        val shareYoutubeHistory: Boolean,
        val shareLiveAudio: Boolean,
        val shareLiveCamera: Boolean,
        val shareScreenView: Boolean,
        val shareAppControl: Boolean,
        val shareSms: Boolean,
        val shareCallLog: Boolean,
        val shareMedia: Boolean,
    )


    data class PartnerResponse(
        val linked: Boolean,
        val partnerName: String?,
        val partnerSharing: PartnerSharing?,
        val telemetry: PartnerTelemetry?,
        val appControl: AppControlResponse?,
    )


    data class PartnerTelemetry(
        val t: Long,
        val batteryPct: Int,
        val diskFreeBytes: Long,
        val diskTotalBytes: Long,
        val foregroundPackage: String?,
        val foregroundAppLabel: String?,
        val usageStats: List<UsageStatItem>,
        /** Sum of foreground time today (all apps); aligns with Screen Time “Today” tile. */
        val usageTodayTotalMs: Long,
        /** Total foreground time last 7 local days (all apps). */
        val usageWeekTotalMs: Long,
        /** Average foreground time per day over those 7 days (week total / 7). */
        val usageDailyAvgMs: Long,
        val location: LocationData?,
        val networkType: String,
        val networkBars: Int,
        val networkMaxBars: Int,
    )

    data class LocationData(
        val lat: Double,
        val lng: Double,
        val acc: Float,
        val t: Long,
    )

    data class UsageStatItem(
        val packageName: String,
        val label: String,
        val ms: Long,
    )

    data class InstalledAppItem(
        val packageName: String,
        val label: String,
        val lastUsed: Long
    )

    data class ContactItem(

        val name: String,
        val phone: String,
    )

    data class WebHistoryItem(
        val url: String,
        val title: String,
        val timestamp: Long,
    )

    data class YoutubeHistoryItem(
        val title: String,
        val timestamp: Long,
    )

    data class SmsItem(
        val address: String,
        val body: String,
        val timestamp: Long,
        val isIncoming: Boolean,
    )

    data class CallLogItem(
        val number: String,
        val name: String,
        val timestamp: Long,
        val durationSeconds: Long,
        val type: String,
    )

    class ApiException(val code: Int, message: String) : Exception("HTTP $code: $message")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Shared singleton OkHttpClient to avoid thread/connection pool proliferation (#17) */
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .build()
        }

        fun defaultClient(): OkHttpClient = sharedClient

        fun buildTelemetryJson(
            batteryPct: Int,
            diskFreeBytes: Long,
            diskTotalBytes: Long,
            foregroundPackage: String?,
            foregroundAppLabel: String?,
            usageStats: List<Triple<String, String, Long>>,
            usageTodayTotalMs: Long,
            usageWeekTotalMs: Long,
            usageDailyAvgMs: Long,
            networkType: String,
            networkBars: Int,
            networkMaxBars: Int,
        ): JSONObject {
            val arr = JSONArray()
            for ((pkg, label, ms) in usageStats) {
                arr.put(
                    JSONObject()
                        .put("package", pkg)
                        .put("label", label)
                        .put("ms", ms)
                )
            }
            return JSONObject()
                .put("t", System.currentTimeMillis())
                .put("batteryPct", batteryPct)
                .put("diskFreeBytes", diskFreeBytes)
                .put("diskTotalBytes", diskTotalBytes)
                .put("foregroundPackage", foregroundPackage.orEmpty())
                .put("foregroundAppLabel", foregroundAppLabel.orEmpty())
                .put("usageStats", arr)
                .put("usageTodayTotalMs", usageTodayTotalMs)
                .put("usageWeekTotalMs", usageWeekTotalMs)
                .put("usageDailyAvgMs", usageDailyAvgMs)
                .put("networkType", networkType)
                .put("networkBars", networkBars)
                .put("networkMaxBars", networkMaxBars)
        }
    }
}
