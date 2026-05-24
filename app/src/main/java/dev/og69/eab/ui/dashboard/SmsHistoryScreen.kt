package dev.og69.eab.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import dev.og69.eab.network.WebSocketService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Parses and extracts display name from raw address. */
fun getDisplayName(address: String): String {
    if (address.contains("<") && address.endsWith(">")) {
        return address.substringBefore(" <").trim()
    }
    return address
}

/** Parses and extracts phone number from raw address. */
fun getPhoneNumber(address: String): String {
    if (address.contains("<") && address.endsWith(">")) {
        return address.substringAfter("<").substringBefore(">").trim()
    }
    return address
}

/** Data class representing a conversation thread summary. */
data class SmsConversation(
    val contactName: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val messageCount: Int,
    val messages: List<CoupleApi.SmsItem>,
)

/**
 * In-memory cache so the conversation detail screen can read messages
 * already fetched by the list screen, avoiding a redundant API call that
 * could fail independently and show "No messages."
 */
object SmsCache {
    var conversations: List<SmsConversation> = emptyList()
        private set

    fun update(convos: List<SmsConversation>) {
        conversations = convos
    }

    fun messagesFor(contactName: String): List<CoupleApi.SmsItem> {
        return conversations.firstOrNull { it.contactName == contactName }?.messages.orEmpty()
    }

    fun clear() {
        conversations = emptyList()
    }
}

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
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<SmsConversation>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Dialog state for "New Message"
    var showNewSmsDialog by remember { mutableStateOf(false) }
    var contactsList by remember { mutableStateOf<List<CoupleApi.ContactItem>>(emptyList()) }
    var contactsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(retryTrigger) {
        isLoading = true
        errorMessage = null
        conversations = null

        val session = SessionRepository(context).getSession()
        if (session == null) {
            errorMessage = "Not logged in."
            isLoading = false
            return@LaunchedEffect
        }

        // Retry up to 3 times for transient network failures
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                val allSms = api.getPartnerSms(session)
                val convos = allSms
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

                // Cache for conversation detail screen
                SmsCache.update(convos)
                conversations = convos
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) delay(1000L * attempt)
            }
        }

        if (lastError != null) {
            errorMessage = "Failed to load SMS history. Please try again."
            conversations = null
        }
        isLoading = false
    }

    // Proactively fetch contacts list when Dialog opens
    LaunchedEffect(showNewSmsDialog) {
        if (showNewSmsDialog && contactsList.isEmpty()) {
            contactsLoading = true
            val session = SessionRepository(context).getSession()
            if (session != null) {
                try {
                    contactsList = api.getPartnerContacts(session)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    contactsLoading = false
                }
            }
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewSmsDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Message")
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        when {
            isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMessage != null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(errorMessage!!, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { retryTrigger++ }) {
                        Text("Retry")
                    }
                }
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

    // "New Message" Dialog
    if (showNewSmsDialog) {
        NewSmsDialog(
            contacts = contactsList,
            isLoadingContacts = contactsLoading,
            onDismiss = { showNewSmsDialog = false },
            onSend = { recipientPhone, recipientName, messageText ->
                showNewSmsDialog = false
                scope.launch {
                    try {
                        val payload = org.json.JSONObject().apply {
                            put("type", "send_sms")
                            put("phone", recipientPhone)
                            put("body", messageText)
                        }
                        WebSocketService.sendSignaling(payload)

                        // Navigate to new thread
                        val formattedAddress = if (recipientName.isNotEmpty()) {
                            "$recipientName <$recipientPhone>"
                        } else {
                            recipientPhone
                        }
                        onOpenConversation(formattedAddress)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        )
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
                        text = getDisplayName(conversation.contactName).firstOrNull()?.uppercase() ?: "?",
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
                        text = getDisplayName(conversation.contactName),
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
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<CoupleApi.SmsItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Listen to signaling messages for real-time history refresh
    val signalingPayload by WebSocketService.signalingFlow.collectAsState(initial = null)

    LaunchedEffect(signalingPayload) {
        if (signalingPayload != null) {
            val type = signalingPayload!!.optString("type")
            if (type == "sms_history_updated") {
                retryTrigger++
            }
        }
    }

    LaunchedEffect(contactName, retryTrigger) {
        isLoading = true
        errorMessage = null

        // First try to use cached data from the list screen (instant, no network)
        val cached = SmsCache.messagesFor(contactName)
        if (cached.isNotEmpty()) {
            messages = cached.sortedBy { it.timestamp } // Chronological order (oldest first, newest bottom)
            isLoading = false
            return@LaunchedEffect
        }

        // Fallback: fetch from API with retry (handles deep-link or cache miss)
        val session = SessionRepository(context).getSession()
        if (session == null) {
            errorMessage = "Not logged in."
            isLoading = false
            return@LaunchedEffect
        }

        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                val allSms = api.getPartnerSms(session)
                messages = allSms
                    .filter { it.address == contactName }
                    .sortedBy { it.timestamp } // Chronological order (oldest first, newest bottom)
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) delay(1000L * attempt)
            }
        }

        if (lastError != null) {
            errorMessage = "Failed to load messages. Please try again."
            messages = null
        }
        isLoading = false
    }

    // Scroll to the bottom when messages are loaded/updated
    LaunchedEffect(messages?.size) {
        if (!messages.isNullOrEmpty()) {
            listState.animateScrollToItem(messages!!.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getDisplayName(contactName), style = MaterialTheme.typography.titleLarge) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                errorMessage != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(errorMessage!!, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { retryTrigger++ }) {
                            Text("Retry")
                        }
                    }
                }
                messages.isNullOrEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No messages.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(messages!!) { msg ->
                        MessageBubble(msg)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // Chat bottom input bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Text Message") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val body = inputText.trim()
                            if (body.isNotEmpty()) {
                                inputText = ""
                                // Responsive optimistic UI updates
                                val placeholder = CoupleApi.SmsItem(
                                    address = contactName,
                                    body = body,
                                    timestamp = System.currentTimeMillis(),
                                    isIncoming = false
                                )
                                messages = messages.orEmpty() + placeholder

                                scope.launch {
                                    try {
                                        val payload = org.json.JSONObject().apply {
                                            put("type", "send_sms")
                                            put("phone", getPhoneNumber(contactName))
                                            put("body", body)
                                        }
                                        WebSocketService.sendSignaling(payload)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        },
                        enabled = inputText.trim().isNotEmpty() && !isLoading,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(24.dp)
                        )
                    }
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

// ─────────────────────────────────────────────────
//  Screen 3: Custom Premium "New Message" Dialog
// ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSmsDialog(
    contacts: List<CoupleApi.ContactItem>,
    isLoadingContacts: Boolean,
    onDismiss: () -> Unit,
    onSend: (String, String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<CoupleApi.ContactItem?>(null) }

    // Filter contacts based on search query
    val filteredContacts = remember(query, contacts) {
        if (query.isBlank()) emptyList()
        else contacts.filter {
            it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "New Message",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recipient selector or input field
                if (selectedContact == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("To: Name or Phone Number") },
                        placeholder = { Text("Enter number or search contact") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty() && query.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) {
                                Button(
                                    onClick = {
                                        selectedContact = CoupleApi.ContactItem(name = "", phone = query.trim())
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text("Use")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Show filtered contact list
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                    ) {
                        if (isLoadingContacts) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (filteredContacts.isNotEmpty()) {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(filteredContacts) { contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedContact = contact
                                                query = ""
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = contact.name.trim().firstOrNull()?.uppercase() ?: "?",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(contact.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }
                        } else if (query.isNotEmpty()) {
                            Text(
                                "No matching contacts found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                } else {
                    // Recipient Chip / Display Box
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (selectedContact!!.name.takeIf { it.isNotEmpty() }?.firstOrNull()?.uppercase() ?: "#"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    if (selectedContact!!.name.isNotEmpty()) {
                                        Text(selectedContact!!.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                    Text(selectedContact!!.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                                }
                            }
                            IconButton(
                                onClick = { selectedContact = null }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove recipient", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Message Text Field
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    placeholder = { Text("Type a message...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Cancel / Send buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val phone = selectedContact?.phone ?: ""
                            val name = selectedContact?.name ?: ""
                            if (phone.isNotEmpty() && message.trim().isNotEmpty()) {
                                onSend(phone, name, message.trim())
                            }
                        },
                        enabled = selectedContact != null && message.trim().isNotEmpty()
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}
