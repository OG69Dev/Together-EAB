package dev.og69.eab.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.og69.eab.R
import dev.og69.eab.accessibility.AccessibilityHelper
import dev.og69.eab.data.PermissionPreferences
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import dev.og69.eab.telemetry.UsageAccessLauncher
import dev.og69.eab.telemetry.UsageStatsPermission
import dev.og69.eab.telemetry.formatDurationMs
import dev.og69.eab.notifications.NotificationPermission
import dev.og69.eab.ui.permissions.PermissionCheckerSheet
import dev.og69.eab.ui.UpdateDownloadDialog
import dev.og69.eab.work.TelemetryWorkScheduler
import dev.og69.eab.work.UpdateWorkScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSignOut: () -> Unit,
    onEditProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
) {
    val partner by viewModel.partner.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    val manualUpdateRelease by viewModel.manualUpdateRelease.collectAsState()
    val updateSnack by viewModel.updateSnack.collectAsState()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val repo = remember { SessionRepository(appContext) }
    val consentFlow = repo.consentAcceptedFlow.collectAsState(initial = false)
    val consentAccepted by consentFlow
    var showConsent by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var resumeVersion by remember { mutableIntStateOf(0) }
    var showA11yDialog by remember { mutableStateOf(false) }
    var showUsageDialog by remember { mutableStateOf(false) }
    var showPermissionSheet by remember { mutableStateOf(false) }

    val permissionPrefs = remember { PermissionPreferences(appContext) }
    val activity = context as? ComponentActivity
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        scope.launch {
            permissionPrefs.setNotificationsAutoPromptCompleted(true)
        }
    }

    LaunchedEffect(consentAccepted) {
        if (!consentAccepted) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (NotificationPermission.hasPostNotifications(context)) {
            permissionPrefs.setNotificationsAutoPromptCompleted(true)
            return@LaunchedEffect
        }
        if (permissionPrefs.getNotificationsAutoPromptCompleted()) return@LaunchedEffect
        if (activity == null) return@LaunchedEffect
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeVersion++
                TelemetryWorkScheduler.enqueueImmediate(appContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(consentAccepted) {
        showConsent = !consentAccepted
    }

    val a11yEnabled = remember(resumeVersion) {
        AccessibilityHelper.isOurServiceEnabled(appContext)
    }
    val usageEnabled = remember(resumeVersion) {
        UsageStatsPermission.has(appContext)
    }
    val notificationGranted = remember(resumeVersion) {
        NotificationPermission.hasPostNotifications(appContext)
    }

    LaunchedEffect(consentAccepted, resumeVersion, a11yEnabled, usageEnabled) {
        if (!consentAccepted) {
            showA11yDialog = false
            showUsageDialog = false
            return@LaunchedEffect
        }
        when {
            !a11yEnabled -> {
                showA11yDialog = true
                showUsageDialog = false
            }
            !usageEnabled -> {
                showA11yDialog = false
                showUsageDialog = true
            }
            else -> {
                showA11yDialog = false
                showUsageDialog = false
            }
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snack.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(updateSnack) {
        updateSnack?.let {
            snack.showSnackbar(it)
            viewModel.clearUpdateSnack()
        }
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.uploadAndRefresh()
                delay(30_000)
            }
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.consent_title)) },
            text = { Text(stringResource(R.string.consent_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.setConsentAccepted(true)
                            showConsent = false
                        }
                    },
                ) { Text(stringResource(R.string.consent_agree)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.signOut()
                            showConsent = false
                            onSignOut()
                        }
                    },
                ) {
                    Text(stringResource(R.string.consent_decline))
                }
            },
        )
    }

    if (consentAccepted && showA11yDialog && !a11yEnabled) {
        AlertDialog(
            onDismissRequest = { showA11yDialog = false },
            title = { Text(stringResource(R.string.dialog_a11y_title)) },
            text = { Text(stringResource(R.string.dialog_a11y_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showA11yDialog = false
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                ) { Text(stringResource(R.string.dialog_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showA11yDialog = false }) {
                    Text(stringResource(R.string.dialog_later))
                }
            },
        )
    }

    if (consentAccepted && showUsageDialog && !usageEnabled && a11yEnabled) {
        AlertDialog(
            onDismissRequest = { showUsageDialog = false },
            title = { Text(stringResource(R.string.dialog_usage_title)) },
            text = { Text(stringResource(R.string.dialog_usage_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUsageDialog = false
                        UsageAccessLauncher.openForOurApp(context)
                    },
                ) { Text(stringResource(R.string.dialog_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showUsageDialog = false }) {
                    Text(stringResource(R.string.dialog_later))
                }
            },
        )
    }

    manualUpdateRelease?.let { rel ->
        UpdateDownloadDialog(
            releaseInfo = rel,
            onDismiss = { viewModel.clearManualUpdateRelease() },
            onConfirmDownload = {
                UpdateWorkScheduler.enqueueDownload(appContext, rel.apkDownloadUrl)
                viewModel.clearManualUpdateRelease()
            },
        )
    }

    if (showPermissionSheet) {
        PermissionCheckerSheet(
            onDismiss = { showPermissionSheet = false },
            accessibilityGranted = a11yEnabled,
            usageGranted = usageEnabled,
            notificationsGranted = notificationGranted,
            onRequestNotificationPermission = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(
                        onClick = { viewModel.uploadAndRefresh() },
                        enabled = !refreshing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_check_updates)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.checkForUpdates()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_permissions)) },
                                onClick = {
                                    menuOpen = false
                                    showPermissionSheet = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_sharing_and_name)) },
                                onClick = {
                                    menuOpen = false
                                    onEditProfile()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_out)) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        viewModel.signOut()
                                        onSignOut()
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                val a11yOk = remember(resumeVersion) {
                    AccessibilityHelper.isOurServiceEnabled(appContext)
                }
                val usageOk = remember(resumeVersion) {
                    UsageStatsPermission.has(appContext)
                }
                when {
                    !a11yOk || !usageOk -> {
                        LocalPermissionsBanner(
                            context = appContext,
                            a11yOk = a11yOk,
                            usageOk = usageOk,
                            onReviewPermissions = { showPermissionSheet = true },
                        )
                    }
                    else -> { }
                }
            }
            item {
                PartnerCard(partner)
            }
            item {
                val usageHidden = partner?.partnerSharing.isUsageHidden()
                Text(
                    stringResource(R.string.partner_screen_time_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(
                        if (usageHidden) R.string.partner_screen_time_not_shared_sub
                        else R.string.partner_app_usage_sub,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val usageHidden = partner?.partnerSharing.isUsageHidden() == true
                val usage = partner?.telemetry?.usageStats.orEmpty()
                PartnerScreenTimeSummaryTiles(
                    usageHidden = usageHidden,
                    usage = usage,
                    telemetry = partner?.telemetry,
                )
            }
            val usageHidden = partner?.partnerSharing.isUsageHidden() == true
            val usage = partner?.telemetry?.usageStats.orEmpty()
            when {
                usageHidden -> Unit
                usage.isEmpty() -> {
                    item {
                        Text(
                            stringResource(R.string.usage_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    item(key = "partner_usage_apps") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.partner_app_usage_list_header),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PartnerUsageAppsCard(usage = usage)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Shown only while Accessibility or Usage access is still missing on this phone. */
@Composable
private fun LocalPermissionsBanner(
    context: Context,
    a11yOk: Boolean,
    usageOk: Boolean,
    onReviewPermissions: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.permissions_banner_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!a11yOk) {
                Text(
                    stringResource(R.string.permissions_need_accessibility),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!usageOk) {
                Text(
                    stringResource(R.string.permissions_need_usage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onReviewPermissions) {
                Text(stringResource(R.string.permission_banner_review))
            }
        }
    }
}

private fun CoupleApi.PartnerSharing?.isHidden(key: String): Boolean {
    val s = this ?: return false
    if (s.shareAll) return false
    return key in s.hidden
}

private fun CoupleApi.PartnerSharing?.isUsageHidden(): Boolean = isHidden("usageStats")

private fun sanitizeDisplay(s: String?): String? {
    val t = s?.trim() ?: return null
    if (t.isEmpty() || t.equals("null", ignoreCase = true)) return null
    return t
}

@Composable
private fun PartnerCard(response: CoupleApi.PartnerResponse?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.partner_snapshot),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val partnerLabel = sanitizeDisplay(response?.partnerName)
            if (!partnerLabel.isNullOrBlank()) {
                Text(
                    stringResource(R.string.partner_name_as, partnerLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                response == null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.loading_partner))
                }
                !response.linked -> {
                    Text(
                        stringResource(R.string.waiting_partner),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                response.telemetry == null -> {
                    Text(stringResource(R.string.partner_no_telemetry))
                }
                else -> {
                    val t = response.telemetry
                    val sharing = response.partnerSharing
                    if (sharing.isHidden("battery")) {
                        Text(
                            stringResource(R.string.metric_not_shared_battery),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(R.string.battery_label, t.batteryPct),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        LinearProgressIndicator(
                            progress = { t.batteryPct / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                        )
                    }
                    if (sharing.isHidden("storage")) {
                        Text(
                            stringResource(R.string.metric_not_shared_storage),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val totalBytes = t.diskTotalBytes.coerceAtLeast(1L)
                        val freeGb = t.diskFreeBytes / (1024f * 1024f * 1024f)
                        val totalGb = totalBytes / (1024f * 1024f * 1024f)
                        Text(
                            stringResource(R.string.storage_label, freeGb, totalGb),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (sharing.isHidden("currentApp")) {
                        Text(
                            stringResource(R.string.metric_not_shared_current_app_line),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(R.string.current_app_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val appTitle = sanitizeDisplay(t.foregroundAppLabel)
                            ?: sanitizeDisplay(t.foregroundPackage)
                        Text(
                            appTitle ?: stringResource(R.string.unknown_app),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.current_app_usage_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Screen Time–style summary row: always three tiles (Today / week / daily), synced from partner telemetry. */
@Composable
private fun PartnerScreenTimeSummaryTiles(
    usageHidden: Boolean,
    usage: List<CoupleApi.UsageStatItem>,
    telemetry: CoupleApi.PartnerTelemetry?,
) {
    val scroll = rememberScrollState()
    val notShared = stringResource(R.string.partner_screen_time_status_not_shared)
    val listSumMs = usage.sumOf { it.ms }
    val todayMs = when {
        usageHidden -> 0L
        else -> {
            val fromTel = telemetry?.usageTodayTotalMs ?: 0L
            when {
                fromTel > 0L -> fromTel
                listSumMs > 0L -> listSumMs
                else -> 0L
            }
        }
    }
    val weekMs = if (usageHidden) 0L else (telemetry?.usageWeekTotalMs ?: 0L)
    val dailyMs = if (usageHidden) 0L else (telemetry?.usageDailyAvgMs ?: 0L)
    val todayValue = if (usageHidden) notShared else todayMs.formatDurationMs()
    val weekValue = if (usageHidden) notShared else weekMs.formatDurationMs()
    val dailyValue = if (usageHidden) notShared else dailyMs.formatDurationMs()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTimePeriodTile(
            periodLabel = stringResource(R.string.partner_screen_time_tile_today),
            valueLabel = todayValue,
            caption = stringResource(R.string.partner_screen_time_tile_today_caption),
            valueMuted = usageHidden,
        )
        ScreenTimePeriodTile(
            periodLabel = stringResource(R.string.partner_screen_time_tile_week),
            valueLabel = weekValue,
            caption = stringResource(R.string.partner_screen_time_tile_week_caption),
            valueMuted = usageHidden,
        )
        ScreenTimePeriodTile(
            periodLabel = stringResource(R.string.partner_screen_time_tile_daily),
            valueLabel = dailyValue,
            caption = stringResource(R.string.partner_screen_time_tile_daily_caption),
            valueMuted = usageHidden,
        )
    }
}

@Composable
private fun ScreenTimePeriodTile(
    periodLabel: String,
    valueLabel: String,
    caption: String,
    valueMuted: Boolean,
    modifier: Modifier = Modifier,
) {
    val valueColor = if (valueMuted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = modifier.widthIn(min = 152.dp, max = 168.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                periodLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun PartnerUsageAppsCard(
    usage: List<CoupleApi.UsageStatItem>,
    modifier: Modifier = Modifier,
) {
    val maxMs = usage.maxOfOrNull { it.ms }?.coerceAtLeast(1L) ?: 1L
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            usage.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                }
                UsageStatRow(row = row, maxMs = maxMs)
            }
        }
    }
}

@Composable
private fun UsageAppIcon(
    packageName: String,
    displayLabel: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconDp = 40.dp
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .size(iconDp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        val initial = displayLabel.trim().firstOrNull { !it.isWhitespace() }
            ?: packageName.firstOrNull()
            ?: '?'
        Surface(
            modifier = modifier.size(iconDp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    initial.uppercaseChar().toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun UsageStatRow(
    row: CoupleApi.UsageStatItem,
    maxMs: Long,
) {
    val displayName = row.label.ifBlank { row.packageName }
    val progress = (row.ms / maxMs.toFloat()).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UsageAppIcon(packageName = row.packageName, displayLabel = displayName)
            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (row.label.isNotBlank() && row.packageName.isNotBlank()) {
                    Text(
                        row.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                row.ms.formatDurationMs(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}
