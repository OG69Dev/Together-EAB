package dev.og69.eab.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.ui.dashboard.MainHostScreen
import dev.og69.eab.ui.onboarding.OnboardingScreen
import dev.og69.eab.ui.onboarding.ProfileSetupScreen
import dev.og69.eab.ui.dashboard.ContactsScreen
import dev.og69.eab.ui.dashboard.WebHistoryScreen
import dev.og69.eab.ui.dashboard.SmsHistoryScreen
import dev.og69.eab.ui.dashboard.SmsConversationScreen
import dev.og69.eab.ui.dashboard.CallLogScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNav(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val appContext = context.applicationContext

    NavHost(
        navController = nav,
        startDestination = "splash",
        modifier = modifier,
    ) {
        composable("splash") {
            LaunchedEffect(Unit) {
                val repo = SessionRepository(appContext)
                val session = repo.getSession()
                when {
                    session == null -> {
                        nav.navigate("onboarding") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    !repo.isProfileCompleted() -> {
                        nav.navigate("profile_setup_first") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                    else -> {
                        nav.navigate("dashboard") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        composable("onboarding") {
            OnboardingScreen(
                onPaired = {
                    nav.navigate("profile_setup_first") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }
        composable("profile_setup_first") {
            ProfileSetupScreen(
                onSaved = {
                    nav.navigate("dashboard") {
                        popUpTo("profile_setup_first") { inclusive = true }
                    }
                },
            )
        }
        composable("profile_setup_edit") {
            ProfileSetupScreen(
                onSaved = {
                    nav.popBackStack()
                },
            )
        }
        composable("dashboard") {
            MainHostScreen(
                onSignOut = {
                    nav.navigate("onboarding") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onEditProfile = {
                    nav.navigate("profile_setup_edit")
                },
                onNavigateToContacts = {
                    nav.navigate("contacts")
                },
                onNavigateToWebHistory = {
                    nav.navigate("web_history")
                },
                onNavigateToSmsHistory = {
                    nav.navigate("sms_history")
                },
                onNavigateToCallLog = {
                    nav.navigate("call_log")
                },
            )
        }
        composable("contacts") {
            ContactsScreen(
                onBack = { nav.popBackStack() }
            )
        }
        composable("web_history") {
            WebHistoryScreen(onBack = { nav.popBackStack() })
        }
        composable("sms_history") {
            SmsHistoryScreen(
                onBack = { nav.popBackStack() },
                onOpenConversation = { contactName ->
                    nav.navigate("sms_conversation/${URLEncoder.encode(contactName, "UTF-8")}")
                },
            )
        }
        composable("sms_conversation/{contact}") { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("contact") ?: ""
            val contactName = URLDecoder.decode(encoded, "UTF-8")
            SmsConversationScreen(
                contactName = contactName,
                onBack = { nav.popBackStack() },
            )
        }
        composable("call_log") {
            CallLogScreen(onBack = { nav.popBackStack() })
        }
    }
}
