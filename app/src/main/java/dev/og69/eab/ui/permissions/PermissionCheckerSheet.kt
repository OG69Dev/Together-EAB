package dev.og69.eab.ui.permissions

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.og69.eab.R
import dev.og69.eab.telemetry.UsageAccessLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCheckerSheet(
    onDismiss: () -> Unit,
    accessibilityGranted: Boolean,
    usageGranted: Boolean,
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.permission_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.permission_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            PermissionRow(
                title = stringResource(R.string.permission_row_accessibility),
                description = stringResource(R.string.permission_row_accessibility_desc),
                granted = accessibilityGranted,
                grantedLabel = stringResource(R.string.permission_status_on),
                deniedLabel = stringResource(R.string.permission_status_off),
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            PermissionRow(
                title = stringResource(R.string.permission_row_usage),
                description = stringResource(R.string.permission_row_usage_desc),
                granted = usageGranted,
                grantedLabel = stringResource(R.string.permission_status_on),
                deniedLabel = stringResource(R.string.permission_status_off),
                onOpenSettings = { UsageAccessLauncher.openForOurApp(context) },
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            val api33 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val showNotifAllow = api33 && !notificationsGranted
            PermissionRow(
                title = stringResource(R.string.permission_row_notifications),
                description = if (api33) {
                    stringResource(R.string.permission_row_notifications_desc)
                } else {
                    stringResource(R.string.permission_row_notifications_desc_legacy)
                },
                granted = if (api33) notificationsGranted else true,
                grantedLabel = if (api33) {
                    stringResource(R.string.permission_status_on)
                } else {
                    stringResource(R.string.permission_status_not_required)
                },
                deniedLabel = stringResource(R.string.permission_status_off),
                onOpenSettings = {
                    dev.og69.eab.notifications.NotificationPermission.openAppNotificationSettings(context)
                },
                onSecondaryAction = if (showNotifAllow) onRequestNotificationPermission else null,
                secondaryActionLabel = if (showNotifAllow) {
                    stringResource(R.string.permission_action_allow_notifications)
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    grantedLabel: String,
    deniedLabel: String,
    onOpenSettings: () -> Unit,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (granted) grantedLabel else deniedLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_action_open_settings))
            }
            if (onSecondaryAction != null && secondaryActionLabel != null) {
                TextButton(onClick = onSecondaryAction) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}
