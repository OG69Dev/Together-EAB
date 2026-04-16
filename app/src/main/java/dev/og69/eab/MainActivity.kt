package dev.og69.eab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.ui.MainScreen
import dev.og69.eab.ui.MainViewModel
import dev.og69.eab.ui.theme.TogetherEabTheme
import androidx.activity.result.contract.ActivityResultContracts
import android.media.projection.MediaProjectionManager
import android.content.Context
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            dev.og69.eab.network.WebSocketService.setScreenCaptureResult(result.resultCode, result.data!!)
            // Move to background so the user can show other apps
            moveTaskToBack(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.consumeNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            TogetherEabTheme {
                MainScreen(viewModel = mainViewModel)
            }
        }
        
        lifecycleScope.launch {
            val session = SessionRepository(applicationContext).getSession()
            if (session != null) {
                dev.og69.eab.network.WebSocketService.start(applicationContext, session)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel.consumeNotificationIntent(intent)
    }
    override fun onResume() {
        super.onResume()
        dev.og69.eab.work.TelemetryWorkScheduler.enqueueImmediate(applicationContext)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent?) {
        if (intent?.action == "REQUEST_SCREEN_CAPTURE") {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            // Clear the action so it doesn't trigger again on resume
            intent.action = null
        }
    }

    companion object {
        fun requestScreenCapture(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "REQUEST_SCREEN_CAPTURE"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }
}
