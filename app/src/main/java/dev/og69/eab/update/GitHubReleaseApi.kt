package dev.og69.eab.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubReleaseApi(
    private val client: OkHttpClient = defaultClient(),
) {

    fun fetchLatestRelease(owner: String, repo: String, token: String): Result<ReleaseInfo> = runCatching {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .apply {
                if (token.isNotEmpty()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("GitHub API ${resp.code}: $body")
            }
            val j = JSONObject(body)
            val tagName = j.optString("tag_name", "").trim()
            if (tagName.isEmpty()) error("Missing tag_name")
            val assets = j.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url", "")
                    apkSize = a.optLong("size", 0L)
                    break
                }
            }
            val urlFinal = apkUrl?.takeIf { it.isNotEmpty() }
                ?: error("No .apk asset on latest release")
            ReleaseInfo(
                tagName = tagName,
                normalizedVersion = VersionComparator.normalizeTag(tagName),
                apkDownloadUrl = urlFinal,
                apkSizeBytes = apkSize,
            )
        }
    }

    companion object {
        fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
