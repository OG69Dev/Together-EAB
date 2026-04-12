package dev.og69.eab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.og69.eab.ui.MainScreen
import dev.og69.eab.ui.MainViewModel
import dev.og69.eab.ui.theme.TogetherEabTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.consumeNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            TogetherEabTheme {
                MainScreen(viewModel = mainViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel.consumeNotificationIntent(intent)
    }
}
