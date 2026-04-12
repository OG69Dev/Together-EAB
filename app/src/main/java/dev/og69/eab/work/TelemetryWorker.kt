package dev.og69.eab.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import dev.og69.eab.telemetry.DeviceMetrics
import dev.og69.eab.telemetry.ForegroundResolver
import dev.og69.eab.telemetry.UsageStatsPermission

class TelemetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = SessionRepository(applicationContext)
        val session = repo.getSession() ?: return Result.success()
        val usage: List<Triple<String, String, Long>>
        val todayTotalMs: Long
        val weekTotalMs: Long
        val dailyAvgMs: Long
        if (UsageStatsPermission.has(applicationContext)) {
            val (list, todayTotal) = DeviceMetrics.topUsageToday(applicationContext)
            val week = DeviceMetrics.totalForegroundLast7Days(applicationContext)
            usage = list
            todayTotalMs = todayTotal
            weekTotalMs = week
            dailyAvgMs = if (week > 0L) week / 7L else 0L
        } else {
            usage = emptyList()
            todayTotalMs = 0L
            weekTotalMs = 0L
            dailyAvgMs = 0L
        }
        val (free, total) = DeviceMetrics.diskStats(applicationContext)
        val (fgPkg, fgLabel) = ForegroundResolver.resolve(applicationContext)
        val json = CoupleApi.buildTelemetryJson(
            batteryPct = DeviceMetrics.batteryPercent(applicationContext),
            diskFreeBytes = free,
            diskTotalBytes = total,
            foregroundPackage = fgPkg,
            foregroundAppLabel = fgLabel,
            usageStats = usage,
            usageTodayTotalMs = todayTotalMs,
            usageWeekTotalMs = weekTotalMs,
            usageDailyAvgMs = dailyAvgMs,
        )
        val api = CoupleApi()
        return try {
            api.postTelemetry(session, json)
            Result.success()
        } catch (_: IllegalArgumentException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }


}
