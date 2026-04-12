package dev.og69.eab.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.og69.eab.work.UpdateWorkScheduler

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val pending by viewModel.pendingUpdate.collectAsState()
    val context = LocalContext.current

    Box(modifier.fillMaxSize()) {
        AppNav()
        pending?.let { info ->
            UpdateDownloadDialog(
                releaseInfo = info,
                onDismiss = { viewModel.clearPendingUpdate() },
                onConfirmDownload = {
                    UpdateWorkScheduler.enqueueDownload(context.applicationContext, info.apkDownloadUrl)
                    viewModel.clearPendingUpdate()
                },
            )
        }
    }
}
