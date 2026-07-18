package dev.og69.eab.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.og69.eab.R
import dev.og69.eab.network.CoupleApi

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppControlScreen(
    onBack: () -> Unit,
    vm: AppControlViewModel = viewModel()
) {
    val apps by vm.apps.collectAsState()
    val blockedPackages by vm.blockedPackages.collectAsState()
    val blockRules by vm.blockRules.collectAsState()
    val fullPhoneRestrictUntil by vm.fullPhoneRestrictUntil.collectAsState()
    val uninstallBlocked by vm.uninstallBlocked.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val partnerSharing by vm.partnerSharing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // State for restriction duration dialog
    var showDurationSheet by remember { mutableStateOf(false) }
    var pendingRestrictPackage by remember { mutableStateOf<String?>(null) }
    
    // State for full phone restrict duration dialog
    var showFullPhoneDurationSheet by remember { mutableStateOf(false) }
    
    // State for custom duration dialog
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var customDurationTarget by remember { mutableStateOf<String?>(null) } // null = full phone, else packageName
    var customSliderValue by remember { mutableFloatStateOf(30f) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_control_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (loading && apps.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (partnerSharing != null && !partnerSharing!!.shareAppControl) {
                // Partner has disabled app control sharing
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "App Control Not Shared",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Your partner chose not to share their app list. You cannot view or restrict their apps while this is disabled.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {

                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(
                                stringResource(R.string.app_control_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(24.dp))
                            
                            // Full Phone Restrict Section
                            val isFullPhoneActive = fullPhoneRestrictUntil != null && fullPhoneRestrictUntil!! > System.currentTimeMillis()
                            FullPhoneRestrictCard(
                                isActive = isFullPhoneActive,
                                expiresAt = fullPhoneRestrictUntil,
                                onToggle = {
                                    if (isFullPhoneActive) {
                                        vm.clearFullPhoneRestrict()
                                    } else {
                                        showFullPhoneDurationSheet = true
                                    }
                                }
                            )
                            
                            Spacer(Modifier.height(24.dp))
                            
                            // App Security Section
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.app_control_eab_header),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            
                            SecurityControlItem(
                                title = stringResource(R.string.app_control_prevent_uninstall),
                                subtitle = stringResource(R.string.app_control_prevent_uninstall_desc),
                                checked = uninstallBlocked,
                                onCheckedChange = { vm.setUninstallBlocked(it) }
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            val pkg = vm.getApplication<android.app.Application>().packageName
                            val isEabRestricted = blockedPackages.contains(pkg) || (blockRules[pkg] != null && blockRules[pkg]!! > System.currentTimeMillis())
                            SecurityControlItem(
                                title = stringResource(R.string.app_control_restrict_eab),
                                subtitle = stringResource(R.string.app_control_restrict_eab_desc),
                                checked = isEabRestricted,
                                onCheckedChange = { 
                                    if (isEabRestricted) {
                                        vm.unrestrictApp(pkg)
                                    } else {
                                        vm.restrictApp(pkg, null) // EAB restrict is always indefinite
                                    }
                                }
                            )
                            
                            Spacer(Modifier.height(32.dp))
                            
                            Text(
                                stringResource(R.string.app_control_partner_apps).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }
                    }

                    items(apps, key = { it.packageName }) { app ->
                        // Skip Together-EAB in the general list as it has a dedicated section
                        if (app.packageName != vm.getApplication<android.app.Application>().packageName) {
                            val ruleUntil = blockRules[app.packageName]
                            val isRestricted = blockedPackages.contains(app.packageName) || (ruleUntil != null && ruleUntil > System.currentTimeMillis())
                            val expiresAt = if (ruleUntil != null && ruleUntil > System.currentTimeMillis()) ruleUntil else null
                             AppControlItem(
                                app = app,
                                isBlocked = isRestricted,
                                expiresAt = expiresAt,
                                onToggle = {
                                    if (isRestricted) {
                                        vm.unrestrictApp(app.packageName)
                                    } else {
                                        pendingRestrictPackage = app.packageName
                                        showDurationSheet = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Duration selection bottom sheet for individual apps
    if (showDurationSheet && pendingRestrictPackage != null) {
        DurationBottomSheet(
            onDismiss = { 
                showDurationSheet = false
                pendingRestrictPackage = null
            },
            onSelectDuration = { minutes ->
                showDurationSheet = false
                val pkg = pendingRestrictPackage!!
                pendingRestrictPackage = null
                vm.restrictApp(pkg, minutes)
            },
            onCustom = {
                showDurationSheet = false
                customDurationTarget = pendingRestrictPackage
                pendingRestrictPackage = null
                customSliderValue = 30f
                showCustomDurationDialog = true
            }
        )
    }
    
    // Duration selection bottom sheet for full phone restrict
    if (showFullPhoneDurationSheet) {
        DurationBottomSheet(
            onDismiss = { showFullPhoneDurationSheet = false },
            onSelectDuration = { minutes ->
                showFullPhoneDurationSheet = false
                vm.setFullPhoneRestrict(minutes)
            },
            onCustom = {
                showFullPhoneDurationSheet = false
                customDurationTarget = null // null means full phone
                customSliderValue = 30f
                showCustomDurationDialog = true
            }
        )
    }
    
    // Custom duration dialog with slider
    if (showCustomDurationDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDurationDialog = false },
            title = { Text("Custom Duration") },
            text = {
                Column {
                    val minutes = customSliderValue.roundToInt()
                    val hours = minutes / 60
                    val mins = minutes % 60
                    val label = if (hours > 0) {
                        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
                    } else {
                        "${mins}m"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = customSliderValue,
                        onValueChange = { customSliderValue = it },
                        valueRange = 5f..480f,
                        steps = 94, // 5-minute increments: (480-5)/5 - 1 = 94
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("8h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCustomDurationDialog = false
                    val minutes = customSliderValue.roundToInt().toLong()
                    if (customDurationTarget == null) {
                        vm.setFullPhoneRestrict(minutes)
                    } else {
                        vm.restrictApp(customDurationTarget!!, minutes)
                    }
                    customDurationTarget = null
                }) {
                    Text("Restrict")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCustomDurationDialog = false
                    customDurationTarget = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationBottomSheet(
    onDismiss: () -> Unit,
    onSelectDuration: (Long?) -> Unit, // null = indefinite
    onCustom: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "Restrict Duration",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "How long should this restriction last?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            
            DurationOption(label = "5 minutes", icon = Icons.Default.Timer) { onSelectDuration(5L) }
            DurationOption(label = "15 minutes", icon = Icons.Default.Timer) { onSelectDuration(15L) }
            DurationOption(label = "30 minutes", icon = Icons.Default.Timer) { onSelectDuration(30L) }
            DurationOption(label = "1 hour", icon = Icons.Default.Timer) { onSelectDuration(60L) }
            DurationOption(label = "2 hours", icon = Icons.Default.Timer) { onSelectDuration(120L) }
            DurationOption(label = "Custom", icon = Icons.Default.Timer) { onCustom() }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            
            DurationOption(
                label = "Until I unrestrict",
                icon = Icons.Default.Lock,
                tint = Color.Red
            ) { onSelectDuration(null) }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DurationOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FullPhoneRestrictCard(
    isActive: Boolean,
    expiresAt: Long?,
    onToggle: () -> Unit
) {
    val bgColor = if (isActive) Color.Red.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val iconTint = if (isActive) Color.Red else MaterialTheme.colorScheme.primary
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isActive) Color.Red.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PhonelinkLock,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Full Phone Restrict",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (isActive && expiresAt != null) {
                if (expiresAt == Long.MAX_VALUE) {
                    Text(
                        "Active \u2022 Until you unrestrict",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red.copy(alpha = 0.8f)
                    )
                } else {
                    val remaining = expiresAt - System.currentTimeMillis()
                    val mins = (remaining / 60_000).coerceAtLeast(1)
                    val hours = mins / 60
                    val m = mins % 60
                    val timeStr = if (hours > 0) "${hours}h ${m}m remaining" else "${m}m remaining"
                    Text(
                        "Active \u2022 $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red.copy(alpha = 0.8f)
                    )
                }
            } else {
                Text(
                    "Restrict all apps except calls & messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            if (isActive) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SecurityControlItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AppControlItem(
    app: CoupleApi.InstalledAppItem,
    isBlocked: Boolean,
    expiresAt: Long? = null,
    onToggle: () -> Unit
) {
    val lastUsedStr = if (app.lastUsed > 0) {
        val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(app.lastUsed))
        stringResource(R.string.app_control_last_used, dateStr)
    } else {
        stringResource(R.string.app_control_last_used_never)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Icon Initial Placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isBlocked) Color.Red.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (app.label.takeIf { it.isNotBlank() } ?: "?").take(1).uppercase(),
                color = if (isBlocked) Color.Red else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            if (isBlocked && expiresAt != null) {
                val remaining = expiresAt - System.currentTimeMillis()
                val mins = (remaining / 60_000).coerceAtLeast(1)
                val hours = mins / 60
                val m = mins % 60
                val timeStr = if (hours > 0) "Restricted \u2022 ${hours}h ${m}m left" else "Restricted \u2022 ${m}m left"
                Text(
                    timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red.copy(alpha = 0.8f)
                )
            } else if (isBlocked) {
                Text(
                    "Restricted \u2022 Until unrestricted",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    lastUsedStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onToggle) {
             Icon(
                 imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.Security,
                 contentDescription = null,
                 tint = if (isBlocked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
             )
        }
    }
}
