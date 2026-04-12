package dev.og69.eab.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.og69.eab.update.UpdateConfig
import java.util.concurrent.TimeUnit

object UpdateWorkScheduler {

    private const val PERIODIC = "together_update_check"

    fun schedulePeriodic(context: Context) {
        if (!UpdateConfig.isConfigured()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            24,
            TimeUnit.HOURS,
            1,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
    }

    fun enqueueDownload(context: Context, apkUrl: String) {
        val data = Data.Builder()
            .putString(ApkDownloadWorker.KEY_URL, apkUrl)
            .putString(ApkDownloadWorker.KEY_TOKEN, UpdateConfig.token)
            .build()
        val req = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
