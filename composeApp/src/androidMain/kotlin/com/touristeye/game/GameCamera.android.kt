package com.touristeye.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

actual val gameModeAvailable: Boolean = true

private const val TAG = "TouristeyeGame"
private const val ANALYSIS_INTERVAL_MS = 90L

@Composable
actual fun GameCamera(modifier: Modifier): GameVision {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vision = remember { AndroidGameVision(context.applicationContext) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                previewView.display?.let {
                    preview?.targetRotation = it.rotation
                    analysis?.targetRotation = it.rotation
                }
            }
        }
        displayManager.registerDisplayListener(displayListener, null)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val rotation = previewView.display?.rotation
                val newPreview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                    rotation?.let { r -> it.targetRotation = r }
                }
                val newAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        rotation?.let { r -> it.targetRotation = r }
                        it.setAnalyzer(vision.executor, vision::analyze)
                    }
                preview = newPreview
                analysis = newAnalysis
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, newPreview, newAnalysis,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            displayManager.unregisterDisplayListener(displayListener)
            provider?.unbindAll()
        }
    }
    DisposableEffect(vision) { onDispose { vision.close() } }

    AndroidView(factory = { previewView }, modifier = modifier)
    return vision
}

/** Runs MediaPipe object detection on camera frames and ML Kit labelling on demand. All on the phone. */
private class AndroidGameVision(private val context: Context) : GameVision {
    val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val _detections: MutableState<List<Detection>> = mutableStateOf(emptyList())
    private val _imageSize: MutableState<Pair<Int, Int>?> = mutableStateOf(null)
    override val detections: State<List<Detection>> = _detections
    override val imageSize: State<Pair<Int, Int>?> = _imageSize

    @Volatile private var latest: Bitmap? = null
    @Volatile private var closed = false
    private var lastRun = 0L
    private var detector: ObjectDetector? = null
    private var detectorFailed = false
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())
    }

    private fun detector(): ObjectDetector? {
        if (detector == null && !detectorFailed) {
            detector = try {
                ObjectDetector.createFromOptions(
                    context,
                    ObjectDetector.ObjectDetectorOptions.builder()
                        .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
                        .setRunningMode(RunningMode.IMAGE)
                        .setMaxResults(8)
                        .setScoreThreshold(0.4f)
                        .build(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Object detector unavailable", e)
                detectorFailed = true
                null
            }
        }
        return detector
    }

    fun analyze(proxy: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (closed || now - lastRun < ANALYSIS_INTERVAL_MS) return
            lastRun = now
            val upright = proxy.toBitmap().rotated(proxy.imageInfo.rotationDegrees)
            latest = upright
            val w = upright.width.toFloat()
            val h = upright.height.toFloat()
            val found = detector()?.detect(BitmapImageBuilder(upright).build())?.detections().orEmpty().mapNotNull { d ->
                val category = d.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                val box = d.boundingBox()
                Detection(
                    label = category.categoryName(),
                    score = category.score(),
                    left = (box.left / w).coerceIn(0f, 1f),
                    top = (box.top / h).coerceIn(0f, 1f),
                    right = (box.right / w).coerceIn(0f, 1f),
                    bottom = (box.bottom / h).coerceIn(0f, 1f),
                )
            }
            val size = upright.width to upright.height
            main.post {
                if (!closed) {
                    _imageSize.value = size
                    _detections.value = found
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis failed", e)
        } finally {
            proxy.close()
        }
    }

    override suspend fun identify(x: Float, y: Float): String? {
        val frame = latest ?: return null
        val crop = withContext(Dispatchers.Default) { frame.cropAround(x, y) }
        return suspendCancellableCoroutine { cont ->
            labeler.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener { labels -> cont.resume(labels.maxByOrNull { it.confidence }?.text) }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Labelling failed", e)
                    cont.resume(null)
                }
        }
    }

    fun close() {
        closed = true
        executor.execute {
            detector?.close()
            detector = null
        }
        executor.shutdown()
        labeler.close()
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap =
    if (degrees == 0) this else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

/** A square around a normalised point, a third of the short side, so the labeller sees just the aimed-at thing. */
private fun Bitmap.cropAround(x: Float, y: Float): Bitmap {
    val side = (min(width, height) / 3f).roundToInt().coerceAtLeast(32)
    val cx = (x * width).roundToInt()
    val cy = (y * height).roundToInt()
    val left = max(0, min(width - side, cx - side / 2))
    val top = max(0, min(height - side, cy - side / 2))
    return Bitmap.createBitmap(this, left, top, min(side, width), min(side, height))
}
