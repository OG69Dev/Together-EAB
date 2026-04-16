package dev.og69.eab.webrtc

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import org.webrtc.*

/**
 * A custom WebRTC VideoSink that uses TextureView instead of SurfaceView.
 * This can resolve Z-order and composition issues on some Android devices.
 */
class TextureViewRenderer(context: Context) : TextureView(context), TextureView.SurfaceTextureListener, VideoSink {
    companion object {
        private const val TAG = "TextureViewRenderer"
    }

    private val eglRenderer = EglRenderer(TAG)
    private var frameCount = 0

    init {
        surfaceTextureListener = this
    }

    /**
     * Initializes the underlying EglRenderer.
     * @param sharedContext The shared EGL context, usually from the PeerConnectionFactory.
     */
    fun init(sharedContext: EglBase.Context?) {
        eglRenderer.init(sharedContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
    }

    private var videoWidth = 0
    private var videoHeight = 0

    override fun onFrame(frame: VideoFrame?) {
        frame?.let {
            if (it.rotatedWidth != videoWidth || it.rotatedHeight != videoHeight) {
                videoWidth = it.rotatedWidth
                videoHeight = it.rotatedHeight
                post { updateScalingMatrix() }
            }
        }
        eglRenderer.onFrame(frame)
    }

    private fun updateScalingMatrix() {
        if (videoWidth == 0 || videoHeight == 0 || width == 0 || height == 0) return

        val viewRatio = width.toFloat() / height.toFloat()
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()

        val scaleX: Float
        val scaleY: Float

        if (videoRatio > viewRatio) {
            // Video is wider than view (letterbox on top/bottom)
            // Scale by height to fill width, causing cropping on left/right
            scaleY = 1f
            scaleX = (videoWidth.toFloat() * height / videoHeight) / width
        } else {
            // Video is taller than view (letterbox on sides)
            // Scale by width to fill height, causing cropping on top/bottom
            scaleX = 1f
            scaleY = (videoHeight.toFloat() * width / videoWidth) / height
        }

        val matrix = android.graphics.Matrix()
        matrix.setScale(scaleX, scaleY, width / 2f, height / 2f)
        setTransform(matrix)
    }

    // --- SurfaceTextureListener ---

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        eglRenderer.createEglSurface(surfaceTexture)
        updateScalingMatrix()
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        updateScalingMatrix()
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Log.d(TAG, "onSurfaceTextureDestroyed")
        eglRenderer.releaseEglSurface { }
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}

    /**
     * Releases the renderer resources.
     */
    fun release() {
        eglRenderer.release()
    }
}
