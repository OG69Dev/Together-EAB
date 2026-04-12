package dev.og69.eab.ui.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.og69.eab.R
import dev.og69.eab.data.Session
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi

import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SessionRepository(context.applicationContext) }
    val api = remember { CoupleApi() }

    var mode by remember { mutableIntStateOf(0) }
    var joinCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var createdCode by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0; err = null },
                label = { Text(stringResource(R.string.create_pair)) },
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1; err = null },
                label = { Text(stringResource(R.string.join_pair)) },
            )
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        err?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when (mode) {
            0 -> {
                Text(
                    text = stringResource(R.string.create_pair_help),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            err = null
                            runCatching { api.createCouple() }
                                .onSuccess { r ->
                                    repo.saveSession(
                                        Session(
                                            coupleId = r.coupleId,
                                            deviceId = r.deviceId,
                                            deviceToken = r.deviceToken,
                                        )
                                    )

                                    createdCode = r.coupleCode
                                }
                                .onFailure { e ->
                                    err = e.message ?: "Failed"
                                }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.create_pair))
                }
            }

            1 -> {
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase() },
                    label = { Text(stringResource(R.string.pair_code_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            err = null
                            runCatching { api.joinCouple(joinCode.trim()) }
                                .onSuccess { r ->
                                    repo.saveSession(
                                        Session(
                                            coupleId = r.coupleId,
                                            deviceId = r.deviceId,
                                            deviceToken = r.deviceToken,
                                        )
                                    )

                                    onPaired()
                                }
                                .onFailure { e ->
                                    err = e.message ?: "Failed"
                                }
                            busy = false
                        }
                    },
                    enabled = !busy && joinCode.trim().length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.join_pair))
                }
            }
        }

        createdCode?.let { code ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.share_this_code),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        code,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    IconButton(onClick = {
                        clip.setPrimaryClip(ClipData.newPlainText("pair", code))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_code))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onPaired,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.continue_to_app))
                    }
                }
            }
        }
    }
}
