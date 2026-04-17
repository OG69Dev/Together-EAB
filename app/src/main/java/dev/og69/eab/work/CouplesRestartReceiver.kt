package dev.og69.eab.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.og69.eab.data.SessionRepository
import dev.og69.eab.network.WebSocketService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CouplesRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "dev.og69.eab.RESTART_SYNC") {
            
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val repo = SessionRepository(appContext)
                val session = repo.getSession()
                if (session != null) {
                    WebSocketService.start(appContext, session)
                }
            }
        }
    }
}
