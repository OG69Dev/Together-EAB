package dev.og69.eab.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dev.og69.eab.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        UpdateChecker.runBackgroundCheck(applicationContext).fold(
            onSuccess = { ListenableWorker.Result.success() },
            onFailure = { ListenableWorker.Result.retry() },
        )
    }
}
