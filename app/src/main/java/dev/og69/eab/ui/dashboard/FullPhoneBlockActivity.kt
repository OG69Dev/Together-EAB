package dev.og69.eab.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class FullPhoneBlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val expiryTime = intent.getLongExtra("expiry_time", 0L)

        setContent {
            MaterialTheme {
                FullPhoneBlockScreen(
                    expiryTime = expiryTime,
                    onOpenDialer = {
                        val dialIntent = Intent(Intent.ACTION_DIAL)
                        dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(dialIntent)
                    },
                    onOpenMessages = {
                        val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))
                        smsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(smsIntent)
                    },
                    onReturnHome = {
                        val homeIntent = Intent(Intent.ACTION_MAIN)
                        homeIntent.addCategory(Intent.CATEGORY_HOME)
                        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(homeIntent)
                        finish()
                    }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Prevent back button
    }
}

@Composable
fun FullPhoneBlockScreen(
    expiryTime: Long,
    onOpenDialer: () -> Unit,
    onOpenMessages: () -> Unit,
    onReturnHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glassmorphic Card
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(32.dp)),
            color = Color.White.copy(alpha = 0.05f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Lock Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.Red.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.Red
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Phone Restricted",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "FULL DEVICE LOCK ACTIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Red.copy(alpha = 0.8f),
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Your partner has temporarily restricted your phone. Only emergency calls and messages are allowed.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (expiryTime > 0L) {
                    var remaining by androidx.compose.runtime.remember { 
                        androidx.compose.runtime.mutableLongStateOf(expiryTime - System.currentTimeMillis()) 
                    }
                    androidx.compose.runtime.LaunchedEffect(expiryTime) {
                        while (remaining > 0) {
                            kotlinx.coroutines.delay(1000)
                            remaining = expiryTime - System.currentTimeMillis()
                        }
                    }
                    val hours = (remaining / 3600_000).coerceAtLeast(0)
                    val minutes = ((remaining % 3600_000) / 60_000).coerceAtLeast(0)
                    val seconds = ((remaining % 60_000) / 1000).coerceAtLeast(0)
                    
                    val timeString = if (hours > 0) {
                        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
                    } else {
                        "${minutes}:${seconds.toString().padStart(2, '0')}"
                    }
                    
                    if (remaining > 0) {
                        Text(
                            text = "Unlocks in $timeString",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFFCA28), // Amber
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Unlocking...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF66BB6A), // Green
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Restricted Indefinitely",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFFCA28), // Amber
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Emergency Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EmergencyActionButton(
                        label = "Phone",
                        icon = Icons.Default.Phone,
                        bgColor = Color(0xFF2E7D32).copy(alpha = 0.3f),
                        onClick = onOpenDialer,
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyActionButton(
                        label = "SMS",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        bgColor = Color(0xFF1565C0).copy(alpha = 0.3f),
                        onClick = onOpenMessages,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onReturnHome,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        "Return to Home",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmergencyActionButton(
    label: String,
    icon: ImageVector,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}
