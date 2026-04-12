package dev.og69.eab.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.og69.eab.R
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import dev.og69.eab.update.ReleaseInfo
import dev.og69.eab.update.UpdateChecker
import dev.og69.eab.update.UpdateConfig
import dev.og69.eab.telemetry.DeviceMetrics
import dev.og69.eab.telemetry.ForegroundResolver
import dev.og69.eab.telemetry.UsageStatsPermission
import dev.og69.eab.work.TelemetryWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SessionRepository(app)
    private val api = CoupleApi()
    private val mutex = Mutex()

    private val _partner = MutableStateFlow<CoupleApi.PartnerResponse?>(null)
    val partner: StateFlow<CoupleApi.PartnerResponse?> = _partner.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _manualUpdateRelease = MutableStateFlow<ReleaseInfo?>(null)
    val manualUpdateRelease: StateFlow<ReleaseInfo?> = _manualUpdateRelease.asStateFlow()

    private val _updateSnack = MutableStateFlow<String?>(null)
    val updateSnack: StateFlow<String?> = _updateSnack.asStateFlow()

    init {
        viewModelScope.launch {
            repo.cachedPartnerJsonFlow.collect { json ->
                if (json.isNullOrBlank()) return@collect
                runCatching {
                    _partner.value = api.parsePartnerResponse(JSONObject(json))
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearManualUpdateRelease() {
        _manualUpdateRelease.value = null
    }

    fun clearUpdateSnack() {
        _updateSnack.value = null
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (!UpdateConfig.isConfigured()) {
                _updateSnack.value = app.getString(R.string.update_not_configured)
                return@launch
            }
            val r = withContext(Dispatchers.IO) {
                UpdateChecker.fetchNewerReleaseIfAny(app)
            }
            r.fold(
                onSuccess = { info ->
                    if (info == null) {
                        _updateSnack.value = app.getString(R.string.update_up_to_date)
                    } else {
                        _manualUpdateRelease.value = info
                    }
                },
                onFailure = { e ->
                    _updateSnack.value = e.message ?: app.getString(R.string.update_check_failed)
                },
            )
        }
    }

    fun refreshPartnerOnly() {
        viewModelScope.launch {
            val session = repo.getSession() ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    val partnerJson = api.getPartnerJson(session)
                    repo.saveCachedPartnerJson(partnerJson)
                    api.parsePartnerResponse(JSONObject(partnerJson))
                }
            }
                .onSuccess { _partner.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun uploadAndRefresh() {
        viewModelScope.launch {
            mutex.withLock {
                val session = repo.getSession() ?: return@withLock
                _refreshing.value = true
                try {
                    val ctx = getApplication<Application>()
                    val ut = withContext(Dispatchers.Default) {
                        if (UsageStatsPermission.has(ctx)) {
                            val (list, todayTotal) = DeviceMetrics.topUsageToday(ctx)
                            val week = DeviceMetrics.totalForegroundLast7Days(ctx)
                            UsageTelemetryBundle(
                                usage = list,
                                todayTotalMs = todayTotal,
                                weekTotalMs = week,
                                dailyAvgMs = if (week > 0L) week / 7L else 0L,
                            )
                        } else {
                            UsageTelemetryBundle(emptyList(), 0L, 0L, 0L)
                        }
                    }
                    val (free, total) = withContext(Dispatchers.Default) {
                        DeviceMetrics.diskStats(ctx)
                    }
                    val batt = withContext(Dispatchers.Default) {
                        DeviceMetrics.batteryPercent(ctx)
                    }
                    val (fgPkg, fgLabel) = withContext(Dispatchers.Default) {
                        ForegroundResolver.resolve(ctx)
                    }
                    val json = CoupleApi.buildTelemetryJson(
                        batteryPct = batt,
                        diskFreeBytes = free,
                        diskTotalBytes = total,
                        foregroundPackage = fgPkg,
                        foregroundAppLabel = fgLabel,
                        usageStats = ut.usage,
                        usageTodayTotalMs = ut.todayTotalMs,
                        usageWeekTotalMs = ut.weekTotalMs,
                        usageDailyAvgMs = ut.dailyAvgMs,
                    )
                    withContext(Dispatchers.IO) {
                        api.postTelemetry(session, json)
                        val partnerJson = api.getPartnerJson(session)
                        repo.saveCachedPartnerJson(partnerJson)
                        _partner.value = api.parsePartnerResponse(JSONObject(partnerJson))
                    }
                } catch (e: Exception) {
                    _error.value = e.message ?: "Sync failed"
                } finally {
                    _refreshing.value = false
                }
            }
        }
    }

    suspend fun signOut() {
        dev.og69.eab.network.WebSocketService.stop(getApplication())
        _partner.value = null
        repo.clear()
    }
}

private data class UsageTelemetryBundle(
    val usage: List<Triple<String, String, Long>>,
    val todayTotalMs: Long,
    val weekTotalMs: Long,
    val dailyAvgMs: Long,
)
