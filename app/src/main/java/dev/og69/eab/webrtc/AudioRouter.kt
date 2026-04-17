package dev.og69.eab.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log

class AudioRouter(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalMode: Int = AudioManager.MODE_NORMAL
    private var originalSpeakerphone: Boolean = false

    companion object {
        private const val TAG = "AudioRouter"
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            Log.d(TAG, "Setting speakerphone: $on")
            audioManager.isSpeakerphoneOn = on
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speakerphone", e)
        }
    }

    fun activate() {
        Log.d(TAG, "Activating audio routing")
        originalMode = audioManager.mode
        originalSpeakerphone = audioManager.isSpeakerphoneOn
        
        // Use MODE_IN_COMMUNICATION for VoIP/WebRTC style audio
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        // Default to speakerphone as requested (it's "too quiet" currently)
        audioManager.isSpeakerphoneOn = true
    }

    fun deactivate() {
        Log.d(TAG, "Deactivating audio routing")
        audioManager.isSpeakerphoneOn = originalSpeakerphone
        audioManager.mode = originalMode
    }
}
