package dev.og69.eab.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppControlViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SessionRepository(app)
    private val api = CoupleApi()

    private val _apps = MutableStateFlow<List<CoupleApi.InstalledAppItem>>(emptyList())
    val apps: StateFlow<List<CoupleApi.InstalledAppItem>> = _apps.asStateFlow()

    private val _partnerSharing = MutableStateFlow<CoupleApi.PartnerSharing?>(null)
    val partnerSharing: StateFlow<CoupleApi.PartnerSharing?> = _partnerSharing.asStateFlow()


    private val _blockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages.asStateFlow()

    private val _uninstallBlocked = MutableStateFlow(false)
    val uninstallBlocked: StateFlow<Boolean> = _uninstallBlocked.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            _blockedPackages.value = repo.getBlockedPackages()
            _uninstallBlocked.value = repo.uninstallBlockedFlow.first()
        }
        refresh()
    }


    fun refresh() {
        viewModelScope.launch {
            val session = repo.getSession() ?: return@launch
            _loading.value = true
            try {
                val partnerApps = withContext(Dispatchers.IO) {
                    api.getPartnerInstalledApps(session)
                }
                val partnerResponse = withContext(Dispatchers.IO) {
                    api.getPartner(session)
                }
                _apps.value = partnerApps
                _partnerSharing.value = partnerResponse.partnerSharing
                _blockedPackages.value = partnerResponse.appControl?.blockedPackages?.toSet() ?: emptySet()
                _uninstallBlocked.value = partnerResponse.appControl?.uninstallBlocked == true

            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleBlock(packageName: String) {
        val current = _blockedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _blockedPackages.value = current
        save()
    }

    fun setUninstallBlocked(blocked: Boolean) {
        _uninstallBlocked.value = blocked
        save()
    }

    private fun save() {
        viewModelScope.launch {
            val session = repo.getSession() ?: return@launch
            try {
                withContext(Dispatchers.IO) {
                    api.postAppControl(session, _blockedPackages.value.toList(), _uninstallBlocked.value)
                }
            } catch (e: Exception) {
                _error.value = "Failed to save: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
