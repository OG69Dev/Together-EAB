package dev.og69.eab

import android.app.Application
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.telemetry.UsageStatsPrimer
import dev.og69.eab.update.UpdateNotifier
import dev.og69.eab.work.TelemetryWorkScheduler
import dev.og69.eab.work.UpdateWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CouplesApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        UsageStatsPrimer.prime(this)
        UpdateNotifier.ensureChannel(this)
        TelemetryWorkScheduler.schedulePeriodic(this)
        UpdateWorkScheduler.schedulePeriodic(this)

    }
}
