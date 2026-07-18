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

    private val _blockRules = MutableStateFlow<Map<String, Long>>(emptyMap())
    val blockRules: StateFlow<Map<String, Long>> = _blockRules.asStateFlow()

    private val _fullPhoneRestrictUntil = MutableStateFlow<Long?>(null)
    val fullPhoneRestrictUntil: StateFlow<Long?> = _fullPhoneRestrictUntil.asStateFlow()

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
            _blockRules.value = repo.blockRulesFlow.first()
            _fullPhoneRestrictUntil.value = repo.fullPhoneRestrictUntilFlow.first()
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
                _blockRules.value = partnerResponse.appControl?.blockRules ?: emptyMap()
                _fullPhoneRestrictUntil.value = partnerResponse.appControl?.fullPhoneRestrictUntil
                _uninstallBlocked.value = partnerResponse.appControl?.uninstallBlocked == true
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /** Check if an app is currently restricted (either indefinitely blocked or has an active timed rule). */
    fun isAppRestricted(packageName: String): Boolean {
        if (_blockedPackages.value.contains(packageName)) return true
        val ruleUntil = _blockRules.value[packageName] ?: return false
        return ruleUntil > System.currentTimeMillis()
    }

    /** Get the expiration timestamp for a timed restriction, or null if indefinite or not restricted. */
    fun getRestrictionExpiry(packageName: String): Long? {
        val ruleUntil = _blockRules.value[packageName]
        if (ruleUntil != null && ruleUntil > System.currentTimeMillis()) return ruleUntil
        return null // indefinite or not restricted
    }

    /** Restrict an app. If durationMinutes is null, block indefinitely. Otherwise set a timed restriction. */
    fun restrictApp(packageName: String, durationMinutes: Long?) {
        if (durationMinutes == null) {
            // Indefinite block
            val current = _blockedPackages.value.toMutableSet()
            current.add(packageName)
            _blockedPackages.value = current
            // Remove from timed rules if present
            val rules = _blockRules.value.toMutableMap()
            rules.remove(packageName)
            _blockRules.value = rules
        } else {
            // Timed restriction
            val until = System.currentTimeMillis() + durationMinutes * 60_000L
            val rules = _blockRules.value.toMutableMap()
            rules[packageName] = until
            _blockRules.value = rules
            // Also add to blockedPackages for backward compatibility with enforcement
            val current = _blockedPackages.value.toMutableSet()
            current.add(packageName)
            _blockedPackages.value = current
        }
        save()
    }

    /** Unrestrict an app — remove from both blocked packages and block rules. */
    fun unrestrictApp(packageName: String) {
        val current = _blockedPackages.value.toMutableSet()
        current.remove(packageName)
        _blockedPackages.value = current
        val rules = _blockRules.value.toMutableMap()
        rules.remove(packageName)
        _blockRules.value = rules
        save()
    }

    /** Set full phone restriction. If durationMinutes is null, set indefinitely. */
    fun setFullPhoneRestrict(durationMinutes: Long?) {
        _fullPhoneRestrictUntil.value = if (durationMinutes == null) {
            Long.MAX_VALUE
        } else {
            System.currentTimeMillis() + durationMinutes * 60_000L
        }
        save()
    }

    /** Clear the full phone restriction. */
    fun clearFullPhoneRestrict() {
        _fullPhoneRestrictUntil.value = null
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
                    api.postAppControl(
                        session,
                        _blockedPackages.value.toList(),
                        _blockRules.value,
                        _fullPhoneRestrictUntil.value,
                        _uninstallBlocked.value
                    )
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
