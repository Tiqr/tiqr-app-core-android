package org.tiqr.authenticator.scan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import androidx.annotation.CheckResult
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tiqr.authenticator.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Component to scan a QR code using [androidx.camera].
 * The processing of the camera image to a QR code is delegated to [ScanAnalyzer].
 */
class ScanComponent(
        private val context: Context,
        private val lifecycleOwner: LifecycleOwner,
        private val viewFinder: PreviewView,
        private val viewFinderRatio: Float,
        private val scanResult: (result: String) -> Unit
) : DefaultLifecycleObserver {
    companion object {
        private const val BEEP_VOLUME = 0.1f
        private const val VIBRATE_DURATION = 200L
    }

    //region Camera
    private lateinit var camera: Camera
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview
    private val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
    private lateinit var cameraAnalysis: ImageAnalysis
    private val cameraAnalyzer = ScanAnalyzer(lifecycleOwner, viewFinderRatio, ::onDetected)
    //endregion

    //region Sound
    private val soundPool: SoundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build())
            .build()
    private val beepSound: Int = soundPool.load(context, R.raw.beep, 1)
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    //endregion

    private val lifecycleScope = lifecycleOwner.lifecycleScope

    private val broadcastManager: LocalBroadcastManager = LocalBroadcastManager.getInstance(context)
    private val keyReceiver = ScanKeyEventsReceiver { enable -> camera.cameraControl.enableTorch(enable) }

    init {
        lifecycleOwner.lifecycle.addObserver(this)

        lifecycleScope.launch {
            cameraProvider = initCameraProvider()
            startCamera(cameraProvider)
        }
    }

    /**
     * Initialize the [ProcessCameraProvider]
     */
    private suspend fun initCameraProvider(): ProcessCameraProvider {
        return withContext(Dispatchers.Default) {
            ProcessCameraProvider.getInstance(context).await()
        }
    }

    /**
     * Resume the camera and QR code detection and returns a boolean indicating it's resume result.
     */
    @CheckResult
    fun resumeScanning(): Boolean {
        return if (::cameraProvider.isInitialized) {
            startCamera(cameraProvider)
            true
        } else {
            false
        }
    }

    /**
     * Start the camera and QR code detection
     */
    private fun startCamera(cameraProvider: ProcessCameraProvider) {
        fun aspectRatio(width: Int, height: Int): Int {
            val previewRatio = max(width, height).toDouble() / min(width, height)
            return if (abs(previewRatio - 4.0 / 3.0) <= abs(previewRatio - 16.0 / 9.0)) {
                AspectRatio.RATIO_4_3
            } else {
                AspectRatio.RATIO_16_9
            }
        }

        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        cameraPreview = Preview.Builder()
                .setTargetName("tiqr QR scanner")
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(viewFinder.display.rotation)
                .build()

        cameraAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
                    setAnalyzer(ContextCompat.getMainExecutor(context), cameraAnalyzer)
                }

        camera = cameraProvider.run {
            unbindAll()
            bindToLifecycle(lifecycleOwner, cameraSelector, cameraPreview, cameraAnalysis)
        }.apply {
            cameraPreview.setSurfaceProvider(viewFinder.surfaceProvider)
        }
    }

    /**
     * Stop the camera and QR code detection
     */
    private fun stopCamera() {
        cameraProvider.unbindAll()
    }

    /**
     * A QR code has been detected
     */
    private fun onDetected(qrCode: String) {
        stopCamera()
        alertDetection()
        scanResult.invoke(qrCode)
    }

    /**
     * Beep and vibrate to notify the user.
     */
    private fun alertDetection() {
        fun beep() {
            soundPool.play(beepSound, BEEP_VOLUME, BEEP_VOLUME, 1, 0, 1f)
        }

        fun vibrate() {
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_DURATION, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") // deprecated in API 26
                    vibrator.vibrate(VIBRATE_DURATION)
                }
            }
        }

        // Try to respect user settings for silencing
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                // Beep and vibrate
                beep()
                vibrate()
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                // No beep, only vibrate
                vibrate()
            }
            AudioManager.RINGER_MODE_SILENT -> {
                // No beep nor vibrate
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        broadcastManager.registerReceiver(keyReceiver, ScanKeyEventsReceiver.filter)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)

        broadcastManager.unregisterReceiver(keyReceiver)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        soundPool.release()
    }
}

