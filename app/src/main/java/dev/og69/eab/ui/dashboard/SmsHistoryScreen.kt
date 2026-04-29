package dev.og69.eab.ui.dashboard

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Data class representing a conversation thread summary. */
data class SmsConversation(
    val contactName: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val messageCount: Int,
    val messages: List<CoupleApi.SmsItem>,
)

// ─────────────────────────────────────────────────
//  Screen 1: Conversation List (like Messages app)
// ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsHistoryScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val api = remember { CoupleApi() }
    var conversations by remember { mutableStateOf<List<SmsConversation>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val session = SessionRepository(context).getSession()
            if (session != null) {
                val allSms = api.getPartnerSms(session)
                // Group by contact name/address and build conversation summaries
                conversations = allSms
                    .groupBy { it.address }
                    .map { (contact, msgs) ->
                        val sorted = msgs.sortedByDescending { it.timestamp }
                        SmsConversation(
                            contactName = contact,
                            lastMessage = sorted.first().body,
                            lastTimestamp = sorted.first().timestamp,
                            messageCount = msgs.size,
                            messages = sorted,
                        )
                    }
                    .sortedByDescending { it.lastTimestamp }
            }
        } catch (_: Exception) {
            conversations = emptyList()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS History", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        when {
            isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            conversations.isNullOrEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No SMS history found.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
            ) {
                items(conversations!!) { convo ->
                    ConversationRow(
                        conversation = convo,
                        onClick = { onOpenConversation(convo.contactName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: SmsConversation,
    onClick: () -> Unit,
) {
    val timeFmt = remember {
        SimpleDateFormat("MMM d", Locale.getDefault())
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar circle with first letter
            Surface(
                modifier = Modifier.size(48.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = conversation.contactName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.contactName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = timeFmt.format(Date(conversation.lastTimestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = conversation.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Screen 2: Individual Conversation Thread
// ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsConversationScreen(
    contactName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val api = remember { CoupleApi() }
    var messages by remember { mutableStateOf<List<CoupleApi.SmsItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(contactName) {
        try {
            val session = SessionRepository(context).getSession()
            if (session != null) {
                val allSms = api.getPartnerSms(session)
                messages = allSms
                    .filter { it.address == contactName }
                    .sortedByDescending { it.timestamp }
            }
        } catch (_: Exception) {
            messages = emptyList()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactName, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        when {
            isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            messages.isNullOrEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(messages!!) { msg ->
                    MessageBubble(msg)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(item: CoupleApi.SmsItem) {
    val timeFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val alignment = if (item.isIncoming) Alignment.Start else Alignment.End
    val bgColor = if (item.isIncoming)
        MaterialTheme.colorScheme.surfaceVariant
    else
        MaterialTheme.colorScheme.primaryContainer
    val textColor = if (item.isIncoming)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Surface(
            color = bgColor,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = timeFmt.format(Date(item.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f),
                )
            }
        }
    }
}
