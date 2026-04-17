package dev.og69.eab.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.og69.eab.network.CoupleApi
import java.util.Calendar

object AppHelper {

    fun getInstalledApps(context: Context): List<CoupleApi.InstalledAppItem> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30) // Look back 30 days for last used
        }
        val stats = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())

        return apps.filter { 
            // Filter: User-installed apps (non-system) OR system apps that have a launcher intent (like Chrome)
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || pm.getLaunchIntentForPackage(it.packageName) != null
        }.map { 
            val label = it.loadLabel(pm).toString()
            val lastUsed = stats[it.packageName]?.lastTimeUsed ?: 0L
            CoupleApi.InstalledAppItem(it.packageName, label, lastUsed)
        }.sortedByDescending { it.lastUsed }
    }

    fun computeAppsHash(apps: List<CoupleApi.InstalledAppItem>): String {
        val s = apps.joinToString("|") { "${it.packageName}:${it.lastUsed}" }
        return hashString(s)
    }

    private fun hashString(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
