package dev.og69.eab.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.og69.eab.R
import dev.og69.eab.update.ReleaseInfo
import java.util.Locale

@Composable
fun UpdateDownloadDialog(
    releaseInfo: ReleaseInfo,
    onDismiss: () -> Unit,
    onConfirmDownload: () -> Unit,
) {
    val sizeMb = if (releaseInfo.apkSizeBytes > 0) {
        releaseInfo.apkSizeBytes / (1024.0 * 1024.0)
    } else {
        null
    }
    val sizeLine = if (sizeMb != null && sizeMb > 0) {
        stringResource(R.string.update_dialog_size_mb, String.format(Locale.US, "%.1f", sizeMb))
    } else {
        stringResource(R.string.update_dialog_size_unknown)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_dialog_title, releaseInfo.normalizedVersion)) },
        text = {
            Text(
                stringResource(
                    R.string.update_dialog_body,
                    sizeLine,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDownload) {
                Text(stringResource(R.string.update_dialog_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_dialog_later))
            }
        },
    )
}
