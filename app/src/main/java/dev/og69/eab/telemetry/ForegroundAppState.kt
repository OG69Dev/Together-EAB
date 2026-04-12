package dev.og69.eab.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ForegroundAppState {
    private val _packageName = MutableStateFlow<String?>(null)
    private val _appLabel = MutableStateFlow<String?>(null)

    val packageFlow: StateFlow<String?> = _packageName.asStateFlow()
    val labelFlow: StateFlow<String?> = _appLabel.asStateFlow()

    fun update(packageName: String?, label: String?) {
        _packageName.value = packageName
        _appLabel.value = label
    }
}
