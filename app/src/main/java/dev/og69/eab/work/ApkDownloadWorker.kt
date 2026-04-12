package dev.og69.eab.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dev.og69.eab.R
import dev.og69.eab.update.ApkInstaller
import dev.og69.eab.update.UpdateNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ApkDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext ListenableWorker.Result.failure()
        val token = inputData.getString(KEY_TOKEN).orEmpty()
        val outFile = ApkInstaller.apkFile(applicationContext)
        outFile.parentFile?.mkdirs()
        if (outFile.exists()) outFile.delete()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()

        setForeground(createForegroundInfo(0, 0, indeterminate = true))

        val reqBuilder = Request.Builder().url(url)
        if (token.isNotEmpty() && "github.com" in url) {
            reqBuilder.header("Authorization", "Bearer $token")
            reqBuilder.header("Accept", "application/octet-stream")
        }

        runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("Empty body")
                val len = body.contentLength()
                var readTotal = 0L
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            output.write(buf, 0, r)
                            readTotal += r
                            if (len > 0) {
                                val max = len.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                val prog = readTotal.coerceAtMost(len).toInt().coerceAtMost(max)
                                setForeground(createForegroundInfo(prog, max, indeterminate = false))
                            }
                        }
                    }
                }
            }
        }.onFailure {
            UpdateNotifier.cancelDownloadProgress(applicationContext)
            return@withContext ListenableWorker.Result.retry()
        }

        UpdateNotifier.cancelDownloadProgress(applicationContext)

        if (!ApkInstaller.startInstall(applicationContext, outFile)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApkInstaller.canRequestPackageInstalls(applicationContext)) {
                ApkInstaller.openInstallUnknownAppsSettings(applicationContext)
            }
        }
        ListenableWorker.Result.success()
    }

    private fun createForegroundInfo(progress: Int, max: Int, indeterminate: Boolean): ForegroundInfo {
        UpdateNotifier.ensureChannel(applicationContext)
        val notif = NotificationCompat.Builder(applicationContext, UpdateNotifier.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.update_downloading_title))
            .setProgress(max.coerceAtLeast(0), progress.coerceAtLeast(0), indeterminate)
            .setOngoing(true)
            .build()
        val id = 7102
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                id,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(id, notif)
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_TOKEN = "token"
    }
}
