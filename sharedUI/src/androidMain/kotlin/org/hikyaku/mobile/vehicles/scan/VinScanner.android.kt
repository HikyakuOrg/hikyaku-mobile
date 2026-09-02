package org.hikyaku.mobile.vehicles.scan

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.hikyaku.mobile.vehicles.vin.VIN_MIN_ACCEPT_SCORE
import org.hikyaku.mobile.vehicles.vin.VinFrameAccumulator

/**
 * Frames are only analysed this often. ML Kit on a full-resolution frame takes long enough that
 * running it flat out just queues work behind a saturated pipeline and stutters the preview.
 */
private const val VIN_FRAME_INTERVAL_MS = 300L

/**
 * ImageAnalysis defaults to 640x480, at which a 17-character VIN stamped on a door plate is simply
 * not resolvable. This is the floor for reading one at arm's length.
 */
private val VIN_ANALYSIS_RESOLUTION = Size(1920, 1080)

@Composable
actual fun rememberVinScanner(): VinScannerController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val controller = remember { AndroidVinScannerController(context, scope) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    return controller
}

@Composable
actual fun VinCameraPreview(
    controller: VinScannerController,
    torchEnabled: Boolean,
    modifier: Modifier,
) {
    // The live-frame API needs ImageProxy, which cannot appear in commonMain, so the preview only
    // works with the Android controller. Fails softly rather than throwing: a caller that somehow
    // holds a different implementation gets no viewfinder, not a crash.
    val androidController = controller as? AndroidVinScannerController ?: return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // COMPATIBLE forces TextureView instead of the PERFORMANCE default's SurfaceView. This
    // preview is hosted inside a Dialog (its own Android Window, see ScanVinOverlay), and a
    // SurfaceView's directly-composited surface doesn't reliably show through a Dialog window —
    // the feed renders black while the rest of the Compose UI draws normally.
    val previewView = remember {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    AndroidView(factory = { previewView }, modifier = modifier)

    LaunchedEffect(lifecycleOwner, previewView) {
        val cameraProvider = runCatching { ProcessCameraProvider.getInstance(context).await() }
            .getOrNull()
        if (cameraProvider == null) {
            androidController.onCameraUnavailable()
            return@LaunchedEffect
        }
        provider = cameraProvider

        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            VIN_ANALYSIS_RESOLUTION,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .build()
            .apply {
                setAnalyzer(androidController.analysisExecutor) { proxy ->
                    androidController.analyze(proxy)
                }
            }

        camera = runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }.getOrNull()
        if (camera == null) androidController.onCameraUnavailable()
    }

    LaunchedEffect(camera, torchEnabled) {
        runCatching { camera?.cameraControl?.enableTorch(torchEnabled) }
    }

    // Keyed on Unit, not `provider`: `provider` is set from null to the real instance inside the
    // LaunchedEffect above, and that write is itself a recomposition trigger. Keying on `provider`
    // made this effect's key change on that same recomposition, so Compose disposed the old
    // (null-keyed) instance immediately — and since `provider` is read fresh at dispose time, that
    // premature onDispose call read the just-bound provider and unbound the camera it had only
    // just finished attaching, before a single frame reached the preview.
    DisposableEffect(Unit) {
        onDispose { runCatching { provider?.unbindAll() } }
    }
}

/**
 * Owns the ML Kit clients, the CameraX analysis loop and the generative fallback for one scanner
 * session. Mirrors `AndroidPodDescriberController`: state is Compose state with a private setter,
 * and every teardown call is wrapped so closing can never throw into the UI.
 */
internal class AndroidVinScannerController(
    private val context: Context,
    private val scope: CoroutineScope,
) : VinScannerController {

    private val textRecognizer = vinTextRecognizer()
    private val barcodeScanner = vinBarcodeScanner()
    private val fallback = VinGenAiFallback()
    private val accumulator = VinFrameAccumulator()

    /** Analysis runs off the main thread; CameraX needs an executor it can post frames to. */
    val analysisExecutor = Executors.newSingleThreadExecutor()

    private val analysisInFlight = AtomicBoolean(false)
    private var lastAnalysedAtMs = 0L
    private var modulesReady = false
    private var preparingModules = false
    private var fallbackPrepared = false

    override var state: VinScanState by mutableStateOf(VinScanState.PreparingModels)
        private set

    override fun prepare() {
        if (modulesReady || preparingModules) return
        preparingModules = true
        scope.launch {
            modulesReady = ensureVinModules(context, textRecognizer, barcodeScanner)
            preparingModules = false
            state = if (modulesReady) VinScanState.Scanning else VinScanState.Unsupported
        }
    }

    override fun prepareFallback() {
        if (fallbackPrepared) return
        fallbackPrepared = true
        // Deliberately unobserved: on virtually every device this settles to "unavailable", and
        // that is not worth telling the user about.
        scope.launch { fallback.prepare() }
    }

    override fun scanImage(imageBytes: ByteArray) {
        if (!modulesReady) return
        state = VinScanState.AnalyzingImage
        scope.launch {
            val bitmap = decodeVinBitmap(imageBytes)
            if (bitmap == null) {
                state = VinScanState.Failed
                return@launch
            }
            val fromVision = recogniseVin(bitmap, textRecognizer, barcodeScanner)
                .best
                ?.takeIf { it.score >= VIN_MIN_ACCEPT_SCORE }
            if (fromVision != null) {
                state = VinScanState.Found(fromVision.vin, viaFallback = false)
                return@launch
            }
            // Only now, with OCR and barcode both empty, is the slow path worth its seconds.
            val fromModel = fallback.readVin(bitmap).best?.takeIf { it.score >= VIN_MIN_ACCEPT_SCORE }
            state = if (fromModel != null) {
                VinScanState.Found(fromModel.vin, viaFallback = true)
            } else {
                VinScanState.NotFound
            }
        }
    }

    override fun reset() {
        accumulator.reset()
        state = if (modulesReady) VinScanState.Scanning else VinScanState.Unsupported
    }

    fun onCameraUnavailable() {
        state = VinScanState.Unsupported
    }

    /**
     * One CameraX frame. Three guards keep the pipeline healthy: the interval floor, the in-flight
     * flag, and STRATEGY_KEEP_ONLY_LATEST on the use case itself. Drop any one of them and ML Kit
     * queues behind a saturated pipeline.
     */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val mediaImage = proxy.image
        if (!modulesReady ||
            mediaImage == null ||
            state is VinScanState.Found ||
            now - lastAnalysedAtMs < VIN_FRAME_INTERVAL_MS ||
            !analysisInFlight.compareAndSet(false, true)
        ) {
            proxy.close()
            return
        }
        lastAnalysedAtMs = now

        scope.launch {
            try {
                val extraction = recogniseVinFrame(
                    InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees),
                    textRecognizer,
                    barcodeScanner,
                )
                accumulator.offer(extraction)?.let { vin ->
                    state = VinScanState.Found(vin, viaFallback = false)
                }
            } finally {
                // Closing the proxy before BOTH detectors have finished with it corrupts the
                // next frame, so this has to wait for the whole block.
                proxy.close()
                analysisInFlight.set(false)
            }
        }
    }

    fun close() {
        runCatching { textRecognizer.close() }
        runCatching { barcodeScanner.close() }
        runCatching { fallback.close() }
        runCatching { analysisExecutor.shutdown() }
    }
}
