package dev.og69.eab.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.og69.eab.R
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
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
    var busy by remember { mutableStateOf(false) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            shareContacts = false
        }
    }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val cached = repo.cachedProfileFlow.first()
        if (cached != null) {
            displayName = cached.displayName
            shareAll = cached.shareAll
            shareBattery = cached.shareBattery
            shareStorage = cached.shareStorage
            shareCurrentApp = cached.shareCurrentApp
            shareLocation = cached.shareLocation
            shareContacts = cached.shareContacts
            shareWebHistory = cached.shareWebHistory
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.profile_share_all),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.profile_share_all_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = shareAll,
                onCheckedChange = { 
                    shareAll = it 
                    if (it) contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
            )
        }

        if (!shareAll) {
            Text(
                stringResource(R.string.profile_share_pick),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            ShareRow(
                label = stringResource(R.string.profile_share_battery),
                checked = shareBattery,
                onCheckedChange = { shareBattery = it },
            )
            ShareRow(
                label = stringResource(R.string.profile_share_storage),
                checked = shareStorage,
                onCheckedChange = { shareStorage = it },
            )
            ShareRow(
                label = stringResource(R.string.profile_share_current_app),
                checked = shareCurrentApp,
                onCheckedChange = { shareCurrentApp = it },
            )
            ShareRow(
                label = stringResource(R.string.profile_share_screen_time),
                checked = shareUsage,
                onCheckedChange = { shareUsage = it },
            )
            ShareRow(
                label = "Share Live Location",
                checked = shareLocation,
                onCheckedChange = { shareLocation = it },
            )
            ShareRow(
                label = "Share Contacts",
                checked = shareContacts,
                onCheckedChange = {
                    shareContacts = it
                    if (it) contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
            )
            ShareRow(
                label = "Share Web History",
                checked = shareWebHistory,
                onCheckedChange = { shareWebHistory = it },
            )
        }

        err?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))

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
private fun ShareRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
