package dev.og69.eab.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import dev.og69.eab.telemetry.DeviceMetrics
import dev.og69.eab.telemetry.ForegroundResolver
import dev.og69.eab.telemetry.UsageStatsPermission
import android.Manifest
import android.content.pm.PackageManager
import dev.og69.eab.data.ContactsHelper
import androidx.core.content.ContextCompat
import dev.og69.eab.data.WebHistoryHelper
import kotlinx.coroutines.flow.first

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
        val net = DeviceMetrics.networkStatus(applicationContext)
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
            networkType = net.type,
            networkBars = net.bars,
            networkMaxBars = net.maxBars,
        )
        val api = CoupleApi()
        
        val cachedProfile = repo.cachedProfileFlow.first()
        if (cachedProfile?.shareContacts == true && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val contacts = ContactsHelper.getLocalContacts(applicationContext)
                val hash = ContactsHelper.hashContacts(contacts)
                val lastHash = repo.getLatestContactsHash()
                if (hash != lastHash) {
                    api.postContacts(session, contacts)
                    repo.saveLatestContactsHash(hash)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (cachedProfile?.shareWebHistory == true) {
            try {
                val history = WebHistoryHelper.getLocalHistory(applicationContext)
                val hash = WebHistoryHelper.computeHash(history)
                val lastHash = repo.getLatestWebHistoryHash()
                if (hash != "empty" && hash != lastHash) {
                    api.postWebHistory(session, history)
                    repo.saveLatestWebHistoryHash(hash)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // ── SMS History sync ──
        if (cachedProfile?.shareSms == true && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val sms = dev.og69.eab.data.SmsHelper.getLocalSms(applicationContext)
                val hash = dev.og69.eab.data.SmsHelper.hashSms(sms)
                val lastHash = repo.getLatestSmsHash()
                if (hash != "empty" && hash != lastHash) {
                    api.postSmsHistory(session, sms)
                    repo.saveLatestSmsHash(hash)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ── Call Log sync ──
        if (cachedProfile?.shareCallLog == true && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            try {
                val calls = dev.og69.eab.data.CallLogHelper.getLocalCallLog(applicationContext)
                val hash = dev.og69.eab.data.CallLogHelper.hashCallLog(calls)
                val lastHash = repo.getLatestCallLogHash()
                if (hash != "empty" && hash != lastHash) {
                    api.postCallLog(session, calls)
                    repo.saveLatestCallLogHash(hash)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ── YouTube History sync ──
        if (cachedProfile?.shareYoutubeHistory == true) {
            try {
                val history = dev.og69.eab.data.YoutubeHistoryHelper.getLocalHistory(applicationContext)
                val hash = dev.og69.eab.data.YoutubeHistoryHelper.computeHash(history)
                val lastHash = repo.getLatestYoutubeHash()
                if (hash != "empty" && hash != lastHash) {
                    api.postYoutubeHistory(session, history)
                    repo.saveLatestYoutubeHash(hash)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ── Installed Apps sync ──
        if (cachedProfile?.shareAppControl == true) {
            try {
                val apps = dev.og69.eab.data.AppHelper.getInstalledApps(applicationContext)
                val hash = dev.og69.eab.data.AppHelper.computeAppsHash(apps)
                val lastHash = repo.getLatestAppsHash()
                if (hash != "empty" && hash != lastHash) {
                    api.postInstalledApps(session, apps)
                    repo.saveLatestAppsHash(hash)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }



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
