package dev.og69.eab.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.CoupleApi
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val api = remember { CoupleApi() }
    var history by remember { mutableStateOf<List<CoupleApi.WebHistoryItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val session = SessionRepository(context).getSession()
            if (session != null) {
                history = api.getPartnerWebHistory(session)
            }
        } catch (e: Exception) {
            history = emptyList()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web Control", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val list = history.orEmpty().filter { 
                !it.title.equals("Search...", ignoreCase = true)
            }
            if (list.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No web history found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(list) { item ->
                        WebHistoryRow(item)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WebHistoryRow(item: CoupleApi.WebHistoryItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            // Parse domain or search term
            var domainText = item.url
            var isSearch = false
            try {
                if (item.url.contains("google.") && item.url.contains("search?")) {
                    val q = Uri.parse(item.url).getQueryParameter("q")
                    if (!q.isNullOrBlank()) {
                        domainText = "Searched: $q"
                        isSearch = true
                    }
                } else if (item.url.contains("duckduckgo.com/") && item.url.contains("q=")) {
                    val q = Uri.parse(item.url).getQueryParameter("q")
                    if (!q.isNullOrBlank()) {
                        domainText = "Searched: $q"
                        isSearch = true
                    }
                } else if (item.url.contains("bing.com/search")) {
                    val q = Uri.parse(item.url).getQueryParameter("q")
                    if (!q.isNullOrBlank()) {
                        domainText = "Searched: $q"
                        isSearch = true
                    }
                } else if (!item.url.contains(".") && !item.url.startsWith("http")) {
                    domainText = "Searched: ${item.url}"
                    isSearch = true
                } else {
                    var u = item.url
                    if (!u.startsWith("http")) u = "https://$u"
                    domainText = Uri.parse(u).host ?: item.url
                }
            } catch (e: Exception) {
                // Ignore parsing errors, raw text falls back
            }

            Icon(
                imageVector = if (isSearch) Icons.Rounded.Search else Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domainText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            Text(
                text = timeFmt.format(Date(item.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
