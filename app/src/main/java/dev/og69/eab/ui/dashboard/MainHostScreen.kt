package dev.og69.eab.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle

import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

import dev.og69.eab.work.TelemetryWorkScheduler

@Composable
fun MainHostScreen(
    onSignOut: () -> Unit,
    onEditProfile: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToWebHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val sharedViewModel: DashboardViewModel = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // App-wide lifecycle is managed by MainActivity

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") },
                    label = { Text("Location") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.ChevronRight, contentDescription = "Other") },
                    label = { Text("More") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> DashboardScreen(onSignOut = onSignOut, onEditProfile = onEditProfile, viewModel = sharedViewModel)
                1 -> LocationScreen(onSignOut = onSignOut, viewModel = sharedViewModel)
                2 -> RightEmptyScreen(onNavigateToContacts = onNavigateToContacts, onNavigateToWebHistory = onNavigateToWebHistory)
            }
        }
    }
}
