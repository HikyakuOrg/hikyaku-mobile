package org.hikyaku.mobile.packages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.print.PrintHelper
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hikyaku.mobile.packages.model.PackageDetail

private const val LABEL_WIDTH_PX = 640
private const val CONTENT_PADDING_PX = 40
private const val ROW_SPACING_PX = 14f
private const val QR_SIZE_PX = 200
private const val CODE_GAP_PX = 24
private const val BORDER_STROKE_PX = 4f
private const val DIVIDER_STROKE_PX = 2f

/**
 * The label is laid out in the px units above but rendered at this multiple, so it stays sharp
 * when printed and the barcode gets whole-pixel bars.
 */
private const val LABEL_SCALE = 2

@Composable
actual fun rememberPrintShippingLabel(logoUrl: String?): (detail: PackageDetail) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { detail ->
        scope.launch {
            val logo = logoUrl?.let { loadQrLogo(context, it) }
            val label = withContext(Dispatchers.Default) { buildShippingLabel(detail, logo) }
            PrintHelper(context).apply {
                scaleMode = PrintHelper.SCALE_MODE_FIT
            }.printBitmap("Shipping label ${detail.trackingNumber}", label)
        }
    }
}

/**
 * Loads the org logo through the app's image loader, so it usually comes straight from the cache
 * the detail screen filled. The bitmap is drawn onto a software canvas, so it can't be a hardware
 * one. Returns null if the logo fails to load.
 */
private suspend fun loadQrLogo(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(QR_LOGO_REQUEST_PX)
        .softwareBitmap()
        .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
    return result?.image?.toBitmap()
}

/**
 * Draws a printable label bordered into three sections - a title, a "SHIP TO" recipient block,
 * and a QR code beside a Code 128 barcode of the tracking number - matching the label the web
 * dashboard prints. [logo], when set, goes in the middle of the QR code.
 */
internal fun buildShippingLabel(detail: PackageDetail, logo: Bitmap?): Bitmap {
    val contentWidth = LABEL_WIDTH_PX - CONTENT_PADDING_PX * 2

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 46f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 26f
        isFakeBoldText = true
    }
    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 42f
        isFakeBoldText = true
    }
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
    }
    val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    val linePaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = DIVIDER_STROKE_PX
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = BORDER_STROKE_PX
        style = Paint.Style.STROKE
    }

    val recipientName = detail.receiver.name?.uppercase() ?: "RECIPIENT"
    // Shrink a long name to fit on its one line instead of running off the label.
    val nameWidth = namePaint.measureText(recipientName)
    if (nameWidth > contentWidth) namePaint.textSize *= contentWidth / nameWidth
    val phone = detail.receiver.phone
    val unit = detail.receiver.unit?.takeIf { it.isNotBlank() }
    val address = detail.receiver.address
    val addressLayout = address?.let {
        StaticLayout.Builder.obtain(it, 0, it.length, bodyPaint, contentWidth)
            .setLineSpacing(4f, 1f)
            .build()
    }

    fun Paint.lineHeight() = fontMetrics.descent - fontMetrics.ascent

    val titleSectionHeight = CONTENT_PADDING_PX * 2 + titlePaint.lineHeight()
    val shipToSectionHeight = CONTENT_PADDING_PX * 2 +
        labelPaint.lineHeight() + ROW_SPACING_PX +
        namePaint.lineHeight() + ROW_SPACING_PX +
        (if (phone != null) bodyPaint.lineHeight() + ROW_SPACING_PX else 0f) +
        (if (unit != null) bodyPaint.lineHeight() + ROW_SPACING_PX else 0f) +
        (addressLayout?.height?.toFloat() ?: 0f)
    val codesSectionHeight = CONTENT_PADDING_PX * 2 + QR_SIZE_PX

    val totalHeight = (titleSectionHeight + shipToSectionHeight + codesSectionHeight).toInt()
    val bitmap = Bitmap.createBitmap(
        LABEL_WIDTH_PX * LABEL_SCALE,
        totalHeight * LABEL_SCALE,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    canvas.scale(LABEL_SCALE.toFloat(), LABEL_SCALE.toFloat())

    // Title section
    canvas.drawText(
        "SHIPPING LABEL",
        LABEL_WIDTH_PX / 2f,
        CONTENT_PADDING_PX - titlePaint.fontMetrics.ascent,
        titlePaint,
    )
    var y = titleSectionHeight
    canvas.drawLine(0f, y, LABEL_WIDTH_PX.toFloat(), y, linePaint)

    // Ship-to section
    y += CONTENT_PADDING_PX
    canvas.drawText("SHIP TO:", CONTENT_PADDING_PX.toFloat(), y - labelPaint.fontMetrics.ascent, labelPaint)
    y += labelPaint.lineHeight() + ROW_SPACING_PX
    canvas.drawText(recipientName, CONTENT_PADDING_PX.toFloat(), y - namePaint.fontMetrics.ascent, namePaint)
    y += namePaint.lineHeight() + ROW_SPACING_PX
    if (phone != null) {
        canvas.drawText(phone, CONTENT_PADDING_PX.toFloat(), y - bodyPaint.fontMetrics.ascent, bodyPaint)
        y += bodyPaint.lineHeight() + ROW_SPACING_PX
    }
    if (unit != null) {
        canvas.drawText(unit, CONTENT_PADDING_PX.toFloat(), y - bodyPaint.fontMetrics.ascent, bodyPaint)
        y += bodyPaint.lineHeight() + ROW_SPACING_PX
    }
    if (addressLayout != null) {
        canvas.save()
        canvas.translate(CONTENT_PADDING_PX.toFloat(), y)
        addressLayout.draw(canvas)
        canvas.restore()
        y += addressLayout.height
    }
    y = titleSectionHeight + shipToSectionHeight
    canvas.drawLine(0f, y, LABEL_WIDTH_PX.toFloat(), y, linePaint)

    // Codes section: QR code on the left, barcode with the tracking number under it on the right.
    // Both bitmaps are built at device resolution and drawn 1:1, so no module gets resampled.
    val codesTop = y + CONTENT_PADDING_PX
    val qrLeft = CONTENT_PADDING_PX.toFloat()
    val qr = buildQrBitmap(detail.trackingNumber, QR_SIZE_PX * LABEL_SCALE, logo)
    canvas.drawBitmap(qr, null, RectF(qrLeft, codesTop, qrLeft + QR_SIZE_PX, codesTop + QR_SIZE_PX), null)

    val barcodeLeft = qrLeft + QR_SIZE_PX + CODE_GAP_PX
    val barcodeMaxWidth = LABEL_WIDTH_PX - CONTENT_PADDING_PX - barcodeLeft
    val barsHeight = QR_SIZE_PX - ROW_SPACING_PX - captionPaint.lineHeight()
    val barcode = buildCode128Bitmap(
        detail.trackingNumber,
        maxWidthPx = (barcodeMaxWidth * LABEL_SCALE).toInt(),
        heightPx = (barsHeight * LABEL_SCALE).toInt(),
    )
    val barcodeCenterX = barcodeLeft + barcodeMaxWidth / 2
    if (barcode != null) {
        val width = barcode.width.toFloat() / LABEL_SCALE
        // Snap to a device pixel so each bar lands on whole pixels.
        val left = (((barcodeCenterX - width / 2) * LABEL_SCALE).toInt()).toFloat() / LABEL_SCALE
        canvas.drawBitmap(
            barcode,
            null,
            RectF(left, codesTop, left + width, codesTop + barcode.height.toFloat() / LABEL_SCALE),
            null,
        )
    }
    canvas.drawText(
        detail.trackingNumber,
        barcodeCenterX,
        codesTop + QR_SIZE_PX - captionPaint.fontMetrics.descent,
        captionPaint,
    )

    canvas.drawRect(
        BORDER_STROKE_PX / 2,
        BORDER_STROKE_PX / 2,
        LABEL_WIDTH_PX - BORDER_STROKE_PX / 2,
        totalHeight - BORDER_STROKE_PX / 2,
        borderPaint,
    )

    return bitmap
}

/**
 * Draws a QR code for [text]. With a [logo], error correction goes up to H so a scanner can still
 * recover the modules the logo covers, and the logo sits on a cleared white square in the middle,
 * sized like the one on the package detail screen.
 */
private fun buildQrBitmap(text: String, sizePx: Int, logo: Bitmap?): Bitmap {
    val hints = if (logo != null) mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H) else emptyMap()
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    if (logo == null) return bitmap

    val canvas = Canvas(bitmap)
    val center = sizePx / 2f
    // Size the logo against the code itself, not the quiet zone ZXing pads it with - otherwise it
    // grows into the alignment pattern and the code stops scanning.
    val codeWidth = matrix.enclosingRectangle?.get(2) ?: sizePx
    val logoSide = codeWidth * QR_LOGO_SIZE
    val clearHalf = logoSide * (1 + 2 * QR_LOGO_PADDING) / 2
    canvas.drawRect(
        center - clearHalf,
        center - clearHalf,
        center + clearHalf,
        center + clearHalf,
        Paint().apply { color = Color.WHITE },
    )
    // Fit the logo inside a logoSide square, keeping its aspect ratio.
    val scale = logoSide / maxOf(logo.width, logo.height)
    val halfWidth = logo.width * scale / 2
    val halfHeight = logo.height * scale / 2
    canvas.drawBitmap(
        logo,
        null,
        RectF(center - halfWidth, center - halfHeight, center + halfWidth, center + halfHeight),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
    return bitmap
}

/**
 * Draws a Code 128 barcode for [text], with each module a whole number of pixels as wide as fits
 * in [maxWidthPx]. Returns null if [text] can't be encoded as Code 128, so the label prints
 * without a barcode rather than failing.
 */
private fun buildCode128Bitmap(text: String, maxWidthPx: Int, heightPx: Int): Bitmap? {
    val modules = runCatching { Code128Writer().encode(text) }.getOrNull() ?: return null
    val moduleWidth = maxOf(1, maxWidthPx / modules.size)
    val bitmap = Bitmap.createBitmap(modules.size * moduleWidth, heightPx, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val barPaint = Paint().apply { color = Color.BLACK }
    modules.forEachIndexed { index, isBar ->
        if (isBar) {
            val left = (index * moduleWidth).toFloat()
            canvas.drawRect(left, 0f, left + moduleWidth, heightPx.toFloat(), barPaint)
        }
    }
    return bitmap
}
