package org.hikyaku.mobile.packages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.test.Test
import kotlin.test.assertEquals
import org.hikyaku.mobile.packages.model.PackageDetail
import org.hikyaku.mobile.packages.model.PackageParty
import org.junit.runner.RunWith

/**
 * The shipping label is drawn with android.graphics, so it needs a real device. These tests
 * decode the rendered label with ZXing to prove both codes still scan - in particular that the
 * org logo over the middle of the QR code doesn't make it unreadable.
 *
 * Run with:
 * ```
 * ./gradlew :sharedUI:connectedAndroidDeviceTest --tests "org.hikyaku.mobile.packages.ShippingLabelDeviceTest"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ShippingLabelDeviceTest {

    @Test
    fun qrCodeAndBarcodeDecodeToTheTrackingNumber() {
        val label = buildShippingLabel(detail(), logo = null)

        assertEquals(TRACKING_NUMBER, decode(label, BarcodeFormat.QR_CODE))
        assertEquals(TRACKING_NUMBER, decode(label, BarcodeFormat.CODE_128))
    }

    @Test
    fun qrCodeWithALogoStillDecodes() {
        val label = buildShippingLabel(detail(), logo = fakeLogo())

        assertEquals(TRACKING_NUMBER, decode(label, BarcodeFormat.QR_CODE))
        assertEquals(TRACKING_NUMBER, decode(label, BarcodeFormat.CODE_128))
    }

    @Test
    fun longRecipientNameAndUnitStillLeaveBothCodesReadable() {
        val detail = detail(
            receiver = PackageParty(
                name = "Melbourne Central Pharmacy and Wellness Centre",
                phone = "+61396001099",
                address = "Nauru House, Melbourne, Victoria 3000, Australia",
                unit = "Level 3, Suite 12",
            ),
        )
        val label = buildShippingLabel(detail, logo = fakeLogo())

        assertEquals(TRACKING_NUMBER, decode(label, BarcodeFormat.QR_CODE))
        assertEquals(TRACKING_NUMBER, decode(label, BarcodeFormat.CODE_128))
    }

    private fun decode(bitmap: Bitmap, format: BarcodeFormat): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(format),
            DecodeHintType.TRY_HARDER to true,
        )
        val result = runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints) }
        return checkNotNull(result.getOrNull()) { "No $format found on the label" }.text
    }

    /** A busy, high-contrast square - harder on the QR decoder than a real, mostly flat logo. */
    private fun fakeLogo(): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val cell = size / 8
        for (x in 0 until 8) {
            for (y in 0 until 8) {
                paint.color = if ((x + y) % 2 == 0) Color.BLACK else Color.rgb(230, 90, 30)
                canvas.drawRect(
                    (x * cell).toFloat(),
                    (y * cell).toFloat(),
                    ((x + 1) * cell).toFloat(),
                    ((y + 1) * cell).toFloat(),
                    paint,
                )
            }
        }
        return bitmap
    }

    private fun detail(
        receiver: PackageParty = PackageParty(
            name = "Rose Street Studios",
            phone = "+61396001003",
            address = "60 Rose St, Fitzroy VIC 3065",
        ),
    ) = PackageDetail(
        id = "3f9c2a4e-1b7d-4c8e-9a51-6d2f0e8b7c13",
        trackingNumber = TRACKING_NUMBER,
        createdAt = "2026-09-23T08:00:00",
        currentStatus = "Assigned",
        currentStatusEnum = "ASSIGNED",
        deliveryNotes = null,
        sender = PackageParty(name = "Yarra Same-Day Couriers", phone = null, address = null),
        receiver = receiver,
        warehouseName = null,
        warehouseAddress = null,
        dimensions = null,
        deliveryWindow = null,
        timeline = emptyList(),
    )

    private companion object {
        const val TRACKING_NUMBER = "260923ufMs8GyWSMA"
    }
}
