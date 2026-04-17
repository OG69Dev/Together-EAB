package dev.og69.eab.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RightEmptyScreen(
    onNavigateToContacts: () -> Unit,
    onNavigateToWebHistory: () -> Unit,
    onNavigateToYoutubeHistory: () -> Unit,
    onNavigateToSmsHistory: () -> Unit,
    onNavigateToCallLog: () -> Unit,
    onNavigateToLiveAudio: () -> Unit,
    onNavigateToLiveScreen: () -> Unit,
    onNavigateToMediaBrowser: () -> Unit,
    onNavigateToAppControl: () -> Unit,
    modifier: Modifier = Modifier
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("More", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            MoreMenuItem(icon = Icons.Rounded.Person, label = "Partner Contacts", onClick = onNavigateToContacts)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Public, label = "Web Control", onClick = onNavigateToWebHistory)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.SmartDisplay, label = "YouTube Control", onClick = onNavigateToYoutubeHistory)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Sms, label = "SMS History", onClick = onNavigateToSmsHistory)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Call, label = "Call History", onClick = onNavigateToCallLog)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.Mic, label = "Live Audio", onClick = onNavigateToLiveAudio)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.ScreenshotMonitor, label = "Live Screen View", onClick = onNavigateToLiveScreen)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Rounded.PhotoLibrary, label = "View Photos", onClick = onNavigateToMediaBrowser)
            Spacer(Modifier.height(12.dp))
            MoreMenuItem(icon = Icons.Default.Security, label = "App Control", onClick = onNavigateToAppControl)
        }
    }
}


@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
