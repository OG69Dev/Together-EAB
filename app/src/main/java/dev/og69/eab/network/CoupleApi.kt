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
        )
    }

    private fun parsePartnerSharing(j: JSONObject): PartnerSharing? {
        if (!j.has("partnerSharing") || j.isNull("partnerSharing")) return null
        val o = j.optJSONObject("partnerSharing") ?: return null
        val shareAll = o.optBoolean("shareAll", true)
        val arr = o.optJSONArray("hidden") ?: return PartnerSharing(shareAll = shareAll, hidden = emptyList(), shareLocation = shareAll, shareContacts = shareAll, shareWebHistory = shareAll)
        val hidden = buildList {
            for (i in 0 until arr.length()) {
                val k = arr.optString(i, "").trim()
                if (k.isNotEmpty()) add(k)
            }
        }
        val shareLocation = shareAll || !hidden.contains("location")
        val shareContacts = shareAll || !hidden.contains("contacts")
        val shareWebHistory = shareAll || !hidden.contains("webHistory")
        return PartnerSharing(shareAll = shareAll, hidden = hidden, shareLocation = shareLocation, shareContacts = shareContacts, shareWebHistory = shareWebHistory)
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
    )

    data class PartnerResponse(
        val linked: Boolean,
        val partnerName: String?,
        val partnerSharing: PartnerSharing?,
        val telemetry: PartnerTelemetry?,
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

    data class ContactItem(
        val name: String,
        val phone: String,
    )

    data class WebHistoryItem(
        val url: String,
        val title: String,
        val timestamp: Long,
    )

    class ApiException(val code: Int, message: String) : Exception("HTTP $code: $message")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()

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
        }
    }
}
