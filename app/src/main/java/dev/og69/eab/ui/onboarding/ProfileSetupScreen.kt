package dev.og69.eab.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.og69.eab.R
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MAX_NAME_LEN = 40

@Composable
fun ProfileSetupScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val repo = remember { SessionRepository(appContext) }
    val api = remember { CoupleApi() }

    var displayName by remember { mutableStateOf("") }
    var shareAll by remember { mutableStateOf(true) }
    var shareBattery by remember { mutableStateOf(true) }
    var shareStorage by remember { mutableStateOf(true) }
    var shareCurrentApp by remember { mutableStateOf(true) }
    var shareUsage by remember { mutableStateOf(true) }
    var shareLocation by remember { mutableStateOf(true) }
    var shareContacts by remember { mutableStateOf(false) }
    var shareWebHistory by remember { mutableStateOf(true) }
    var shareYoutubeHistory by remember { mutableStateOf(true) }
    var shareSms by remember { mutableStateOf(false) }
    var shareCallLog by remember { mutableStateOf(false) }
    var shareLiveAudio by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    // Check if permissions are already granted
    val hasContactsPerm = remember {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val hasSmsPerm = remember {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }
    val hasCallLogPerm = remember {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }
    val hasMicPerm = remember {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) shareContacts = false
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) shareSms = false
    }
    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) shareCallLog = false
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) shareLiveAudio = false
    }

    LaunchedEffect(Unit) {
        val cached = repo.cachedProfileFlow.first()
        if (cached != null) {
            displayName = cached.displayName
            shareAll = cached.shareAll
            shareBattery = cached.shareBattery
            shareStorage = cached.shareStorage
            shareCurrentApp = cached.shareCurrentApp
            shareUsage = cached.shareUsage
            shareLocation = cached.shareLocation
            shareContacts = cached.shareContacts
            shareWebHistory = cached.shareWebHistory
            shareYoutubeHistory = cached.shareYoutubeHistory
            shareSms = cached.shareSms
            shareCallLog = cached.shareCallLog
            shareLiveAudio = cached.shareLiveAudio
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_setup_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.profile_setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it.take(MAX_NAME_LEN) },
            label = { Text(stringResource(R.string.profile_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        // ── Share Everything Card ──────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (shareAll)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.profile_share_all),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (shareAll)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.profile_share_all_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shareAll)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked = shareAll,
                    onCheckedChange = { enabled ->
                        shareAll = enabled
                        if (enabled) {
                            shareBattery = true
                            shareStorage = true
                            shareCurrentApp = true
                            shareUsage = true
                            shareLocation = true
                            shareContacts = true
                            shareWebHistory = true
                            shareYoutubeHistory = true
                            shareSms = true
                            shareCallLog = true
                            shareLiveAudio = true
                            // Only prompt permissions if not already granted
                            if (!hasContactsPerm) contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            if (!hasSmsPerm) smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                            if (!hasCallLogPerm) callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                            if (!hasMicPerm) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }
        }

        // ── Individual Toggles (visible when Share All is OFF) ──
        AnimatedVisibility(
            visible = !shareAll,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.profile_share_pick),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )

                ShareToggleCard(
                    icon = Icons.Rounded.BatteryChargingFull,
                    label = stringResource(R.string.profile_share_battery),
                    description = "Battery percentage & charging status",
                    checked = shareBattery,
                    onCheckedChange = { shareBattery = it },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.SdStorage,
                    label = stringResource(R.string.profile_share_storage),
                    description = "Free and total disk space",
                    checked = shareStorage,
                    onCheckedChange = { shareStorage = it },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.PhoneAndroid,
                    label = stringResource(R.string.profile_share_current_app),
                    description = "Currently active app name",
                    checked = shareCurrentApp,
                    onCheckedChange = { shareCurrentApp = it },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.ScreenshotMonitor,
                    label = stringResource(R.string.profile_share_screen_time),
                    description = "Daily and weekly screen time stats",
                    checked = shareUsage,
                    onCheckedChange = { shareUsage = it },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.LocationOn,
                    label = "Share Live Location",
                    description = "Real-time GPS coordinates",
                    checked = shareLocation,
                    onCheckedChange = { shareLocation = it },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.Contacts,
                    label = "Share Contacts",
                    description = "Names and phone numbers",
                    checked = shareContacts,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasContactsPerm) {
                            // Only ask for permission when turning ON and not already granted
                            shareContacts = true
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            // Turning off, or permission already granted — no prompt needed
                            shareContacts = enabled
                        }
                    },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.Language,
                    label = "Share Web History",
                    description = "Websites visited and search queries",
                    checked = shareWebHistory,
                    onCheckedChange = { shareWebHistory = it },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.SmartDisplay,
                    label = "Share YouTube History",
                    description = "Videos watched on the YouTube app",
                    checked = shareYoutubeHistory,
                    onCheckedChange = { shareYoutubeHistory = it },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.Sms,
                    label = "Share SMS History",
                    description = "Text messages sent and received",
                    checked = shareSms,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasSmsPerm) {
                            shareSms = true
                            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                        } else {
                            shareSms = enabled
                        }
                    },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.Call,
                    label = "Share Call History",
                    description = "Incoming, outgoing, and missed calls",
                    checked = shareCallLog,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasCallLogPerm) {
                            shareCallLog = true
                            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                        }
                    },
                )
                ShareToggleCard(
                    icon = Icons.Rounded.Mic,
                    label = "Share Live Audio",
                    description = "Allow partner to listen to your microphone live",
                    checked = shareLiveAudio,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasMicPerm) {
                            shareLiveAudio = true
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            shareLiveAudio = enabled
                        }
                    },
                )
            }
        }

        // ── Error & Progress ──────────────────────────────────
        err?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(4.dp))

        // ── Save Button ───────────────────────────────────────
        Button(
            onClick = {
                val name = displayName.trim()
                if (name.isEmpty()) {
                    err = context.getString(R.string.profile_name_required)
                    return@Button
                }
                scope.launch {
                    busy = true
                    err = null
                    val session = repo.getSession()
                    if (session == null) {
                        err = context.getString(R.string.profile_no_session)
                        busy = false
                        return@launch
                    }
                    val all = shareAll
                    val bat = all || shareBattery
                    val stor = all || shareStorage
                    val cur = all || shareCurrentApp
                    val use = all || shareUsage
                    val loc = all || shareLocation
                    val con = all || shareContacts
                    val hist = all || shareWebHistory
                    val yt = all || shareYoutubeHistory
                    val sms = all || shareSms
                    val cl = all || shareCallLog
                    val la = all || shareLiveAudio
                    runCatching {
                        api.postProfile(
                            session = session,
                            displayName = name,
                            shareAll = all,
                            shareBattery = bat,
                            shareStorage = stor,
                            shareCurrentApp = cur,
                            shareUsage = use,
                            shareLocation = loc,
                            shareContacts = con,
                            shareWebHistory = hist,
                            shareYoutubeHistory = yt,
                            shareLiveAudio = la,
                        )
                    }
                        .onSuccess {
                            repo.saveProfileCache(
                                displayName = name,
                                shareAll = all,
                                shareBattery = bat,
                                shareStorage = stor,
                                shareCurrentApp = cur,
                                shareUsage = use,
                                shareLocation = loc,
                                shareContacts = con,
                                shareWebHistory = hist,
                                shareYoutubeHistory = yt,
                                shareSms = sms,
                                shareCallLog = cl,
                                shareLiveAudio = la,
                                markCompleted = true,
                            )
                            onSaved()
                        }
                        .onFailure { e ->
                            err = e.message ?: context.getString(R.string.profile_save_failed)
                        }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.profile_save_continue))
        }
    }
}

@Composable
private fun ShareToggleCard(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (checked)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (checked)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
