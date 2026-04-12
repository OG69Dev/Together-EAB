package dev.og69.eab.telemetry

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Some OEMs only list an app under “App usage” after it has touched [UsageStatsManager] at least once.
 * This is best-effort and safe if permission is not granted yet (returns empty or is ignored).
 */
object UsageStatsPrimer {

    fun prime(application: Application) {
        try {
            val usm = application.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 86_400_000L * 2
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        } catch (_: SecurityException) {
        } catch (_: Throwable) {
        }
    }
}
