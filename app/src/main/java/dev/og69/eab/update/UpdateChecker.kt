package dev.og69.eab.update

import android.content.Context

object UpdateChecker {

    private val api = GitHubReleaseApi()

    /**
     * Fetches latest GitHub release; returns null if repo not configured, fetch failed, or installed build is up to date.
     */
    fun fetchNewerReleaseIfAny(context: Context): Result<ReleaseInfo?> {
        if (!UpdateConfig.isConfigured()) {
            return Result.success(null)
        }
        val owner = UpdateConfig.owner
        val repo = UpdateConfig.repo
        val token = UpdateConfig.token
        return api.fetchLatestRelease(owner, repo, token).map { info ->
            if (VersionComparator.isRemoteNewer(info.tagName)) info else null
        }
    }

    /**
     * Background check: notify only when newer and [UpdatePreferences] says we have not notified for this tag yet.
     */
    suspend fun runBackgroundCheck(context: Context): kotlin.Result<Unit> {
        if (!UpdateConfig.isConfigured()) return kotlin.Result.success(Unit)
        val info = fetchNewerReleaseIfAny(context).getOrElse { return kotlin.Result.failure(it) }
            ?: return kotlin.Result.success(Unit)
        val prefs = UpdatePreferences(context)
        if (prefs.getLastNotifiedTag() == info.tagName) {
            return kotlin.Result.success(Unit)
        }
        UpdateNotifier.ensureChannel(context)
        UpdateNotifier.showUpdateAvailable(context, info)
        prefs.setLastNotifiedTag(info.tagName)
        return kotlin.Result.success(Unit)
    }
}
