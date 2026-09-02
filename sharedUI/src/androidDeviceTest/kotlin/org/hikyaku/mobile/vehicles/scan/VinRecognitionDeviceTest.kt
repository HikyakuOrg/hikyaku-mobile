package org.hikyaku.mobile.vehicles.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.hikyaku.mobile.vehicles.vin.VinExtraction
import org.hikyaku.mobile.vehicles.vin.VinTextSource
import org.hikyaku.mobile.vehicles.vin.isVinShaped
import org.junit.runner.RunWith

/**
 * The acceptance test for VIN recognition, run against the four real label photos.
 *
 * The ranking logic is covered on the JVM by `VinExtractionTest`; what can only be checked on a
 * device is whether ML Kit actually reads these labels — so this drives
 * [recogniseVin] directly with the real label photos. No Compose, no camera, no fakes.
 *
 * Run with:
 * ```
 * ./gradlew :sharedUI:connectedAndroidDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class VinRecognitionDeviceTest {

    private val textRecognizer = vinTextRecognizer()
    private val barcodeScanner = vinBarcodeScanner()

    /**
     * The install-time `com.google.mlkit.vision.DEPENDENCIES` meta-data lives in `androidApp`,
     * which this test does not install, so the modules have to be requested explicitly here.
     * Without it a fresh device returns four empty results that look exactly like extractor bugs.
     */
    @BeforeTest
    fun installMlKitModules() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ready = runBlocking { ensureVinModules(context, textRecognizer, barcodeScanner) }
        assertTrue(
            ready,
            "Google Play Services could not supply the ocr/barcode modules on this device. " +
                "VIN recognition cannot run here.",
        )
    }

    @AfterTest
    fun closeRecognisers() {
        textRecognizer.close()
        barcodeScanner.close()
    }

    @Test
    fun ktmMotorcycleSticker() {
        val best = assertNotNull(readVin("ktm.png").best, "no VIN found on the KTM sticker")
        assertEquals(KTM_VIN, best.vin)
    }

    @Test
    fun swiftCaravanPlate() {
        val best = assertNotNull(readVin("swift_caravan.webp").best, "no VIN found on the Swift plate")
        assertEquals(SWIFT_VIN, best.vin)
    }

    /** Text runs bottom-to-top; only found because [recogniseVin] retries all four rotations. */
    @Test
    fun toyotaAustraliaPlateRotatedNinetyDegrees() {
        val best = assertNotNull(
            readVin("toyota_au_rotated.webp").best,
            "no VIN found on the rotated Toyota plate",
        )
        assertEquals(TOYOTA_VIN, best.vin)
    }

    /** No readable 17-character text anywhere — the VIN exists only inside a Code 39 barcode. */
    @Test
    fun nissanCanadianComplianceLabelIsReadFromItsBarcode() {
        val best = assertNotNull(
            readVin("nissan_canada_1990.webp").best,
            "no VIN found on the Nissan compliance label",
        )
        assertEquals(VinTextSource.Barcode, best.source, "the VIN is only in the barcode")
        assertTrue(isVinShaped(best.vin), "decoded '${best.vin}' is not VIN-shaped")
    }

    private fun readVin(asset: String): VinExtraction = runBlocking {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("vin/$asset")) {
            "Missing acceptance image vin/$asset. See " +
                "sharedUI/src/androidDeviceTest/resources/vin/README.md for what belongs there."
        }
        val bytes = stream.use { it.readBytes() }
        val bitmap = checkNotNull(decodeVinBitmap(bytes)) { "Could not decode vin/$asset" }
        recogniseVin(bitmap, textRecognizer, barcodeScanner)
    }

    private companion object {
        // Transcribed by eye from the photos. If one of these fails, read the label again before
        // assuming the extractor is at fault — the expected value is the likelier mistake.
        const val KTM_VIN = "VBKV69408GM951812"
        const val SWIFT_VIN = "SGDTT5ESWE0336731"
        const val TOYOTA_VIN = "6T153KK400X332477"
    }
}
