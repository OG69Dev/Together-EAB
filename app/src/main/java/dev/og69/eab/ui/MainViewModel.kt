package dev.og69.eab.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import dev.og69.eab.update.ReleaseInfo
import dev.og69.eab.update.UpdateIntentExtras
import dev.og69.eab.update.VersionComparator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {

    private val _pendingUpdate = MutableStateFlow<ReleaseInfo?>(null)
    val pendingUpdate: StateFlow<ReleaseInfo?> = _pendingUpdate.asStateFlow()

    fun consumeNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateIntentExtras.OPEN_UPDATE, false) != true) return
        val tag = intent.getStringExtra(UpdateIntentExtras.TAG_NAME) ?: return
        val url = intent.getStringExtra(UpdateIntentExtras.APK_URL) ?: return
        val size = intent.getLongExtra(UpdateIntentExtras.APK_SIZE, 0L)
        _pendingUpdate.value = ReleaseInfo(
            tagName = tag,
            normalizedVersion = VersionComparator.normalizeTag(tag),
            apkDownloadUrl = url,
            apkSizeBytes = size,
        )
    }

    fun clearPendingUpdate() {
        _pendingUpdate.value = null
    }
}
