package org.hikyaku.mobile.vehicles.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayInputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.hikyaku.mobile.vehicles.vin.VIN_MIN_ACCEPT_SCORE
import org.hikyaku.mobile.vehicles.vin.VinExtraction
import org.hikyaku.mobile.vehicles.vin.VinTextFragment
import org.hikyaku.mobile.vehicles.vin.VinTextSource
import org.hikyaku.mobile.vehicles.vin.extractVin
import org.hikyaku.mobile.vehicles.vin.mergeExtractions

/**
 * The ML Kit half of VIN recognition, deliberately written as plain functions rather than methods
 * on [VinScannerController]. The instrumented test in `androidDeviceTest` drives these directly
 * with real label photos — no Compose, no camera, no fakes — which is the only way to check OCR
 * accuracy at all.
 */

/** Long edge that a still image is downsampled to before analysis. */
internal const val VIN_MAX_ANALYSIS_DIMENSION = 2048

/** Roughly a minute of waiting for Play Services to land the models, then give up for this session. */
private const val VIN_MODULE_POLL_ATTEMPTS = 30
private const val VIN_MODULE_POLL_INTERVAL_MS = 2_000L

/**
 * Orientations tried in turn on the still-image path. A VIN plate photographed sideways (the
 * Toyota Australia label runs bottom-to-top) is not read by ML Kit's Latin recogniser unaided.
 */
internal val VIN_STILL_ROTATIONS = listOf(0, 90, 180, 270)

internal fun vinTextRecognizer(): TextRecognizer =
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

/**
 * Restricted to the formats that actually appear on vehicle plates. Code 39 is not optional: on
 * older North American compliance labels the VIN exists *only* as a Code 39 barcode, with no
 * readable 17-character text run anywhere on the plate.
 */
internal fun vinBarcodeScanner(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_QR_CODE,
        )
        .build(),
)

/**
 * Ensures Google Play Services has the OCR and barcode modules, requesting them if not.
 *
 * This has to happen before the first frame: while a module is still downloading ML Kit hands back
 * *empty results* rather than failing, so without this a missing model looks exactly like a label
 * with no VIN on it. Returns false when there is no Play Services on the device at all, which is
 * the scanner's cue to report [VinScanState.Unsupported] and leave manual entry to the user.
 */
internal suspend fun ensureVinModules(
    context: Context,
    textRecognizer: TextRecognizer,
    barcodeScanner: BarcodeScanner,
): Boolean = runCatching {
    val client = ModuleInstall.getClient(context)
    suspend fun installed(): Boolean =
        client.areModulesAvailable(textRecognizer, barcodeScanner).await().areModulesAvailable()

    if (installed()) return@runCatching true

    client.installModules(
        ModuleInstallRequest.newBuilder()
            .addApi(textRecognizer)
            .addApi(barcodeScanner)
            .build(),
    ).await()

    // installModules() can resolve once Play Services has accepted the request rather than once
    // the download has landed, so the answer is whether the modules are actually there — polled,
    // not assumed.
    repeat(VIN_MODULE_POLL_ATTEMPTS) {
        if (installed()) return@runCatching true
        delay(VIN_MODULE_POLL_INTERVAL_MS)
    }
    installed()
}.getOrDefault(false)

/**
 * Decodes [bytes] into a bitmap that is the right way up and no larger than
 * [VIN_MAX_ANALYSIS_DIMENSION] on its long edge. Honouring EXIF matters because a photo straight
 * from the camera roll is very often stored rotated with only a tag to say so.
 */
internal fun decodeVinBitmap(bytes: ByteArray): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longEdge <= 0) return@runCatching null

    val options = BitmapFactory.Options().apply {
        inSampleSize = generateSequence(1) { it * 2 }
            .first { sample -> longEdge / sample <= VIN_MAX_ANALYSIS_DIMENSION }
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: return@runCatching null
    applyExifOrientation(decoded, bytes)
}.getOrNull()

/**
 * One recogniser pass over one already-oriented image. Both detectors run concurrently — they are
 * independent, and a VIN can come from either.
 */
internal suspend fun recogniseVinFrame(
    image: InputImage,
    textRecognizer: TextRecognizer,
    barcodeScanner: BarcodeScanner,
): VinExtraction = coroutineScope {
    val text = async { runCatching { textRecognizer.process(image).await() }.getOrNull() }
    val barcodes = async { runCatching { barcodeScanner.process(image).await() }.getOrNull() }
    val fragments = vinFragments(text.await(), barcodes.await().orEmpty())
    if (fragments.isEmpty()) VinExtraction.Empty else extractVin(fragments)
}

/**
 * Still-image path: tries [rotations] in order, stopping early once a result arrives that later
 * rotations cannot beat, and otherwise merging every pass.
 */
internal suspend fun recogniseVin(
    bitmap: Bitmap,
    textRecognizer: TextRecognizer,
    barcodeScanner: BarcodeScanner,
    rotations: List<Int> = VIN_STILL_ROTATIONS,
): VinExtraction {
    val passes = mutableListOf<VinExtraction>()
    for (rotation in rotations) {
        val extraction = recogniseVinFrame(
            InputImage.fromBitmap(bitmap, rotation),
            textRecognizer,
            barcodeScanner,
        )
        passes += extraction
        if (extraction.isConclusive()) break
    }
    return mergeExtractions(*passes.toTypedArray())
}

/**
 * Turns one recogniser result into fragments in reading order.
 *
 * Lines are re-sorted by their bounding box rather than left in block order, because
 * [extractVin] treats list position as reading order when it scores a VIN keyword sitting on the
 * line above a candidate. Barcodes carry no position, so they go last.
 */
internal fun vinFragments(text: Text?, barcodes: List<Barcode>): List<VinTextFragment> {
    val lines = text?.textBlocks.orEmpty()
        .flatMap { block -> block.lines }
        .sortedWith(
            compareBy(
                { line -> line.boundingBox?.top ?: 0 },
                { line -> line.boundingBox?.left ?: 0 },
            ),
        )
        .map { line -> VinTextFragment(line.text, VinTextSource.OcrLine) }
    val codes = barcodes.mapNotNull { it.rawValue }
        .map { raw -> VinTextFragment(raw, VinTextSource.Barcode) }
    return lines + codes
}

/**
 * Whether this pass is good enough that the remaining rotations are not worth paying for: a decoded
 * barcode cannot be improved on, and neither can a strong read whose check digit also agrees.
 */
private fun VinExtraction.isConclusive(): Boolean {
    val candidate = best ?: return false
    if (candidate.score < VIN_MIN_ACCEPT_SCORE) return false
    return candidate.source == VinTextSource.Barcode || candidate.checkDigitValid
}

private fun applyExifOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
    val orientation = runCatching {
        ByteArrayInputStream(bytes).use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bitmap
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}
