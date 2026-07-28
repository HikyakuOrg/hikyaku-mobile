package org.hikyaku.mobile.packages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.print.PrintHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.hikyaku.mobile.packages.model.PackageDetail

private const val LABEL_WIDTH_PX = 640
private const val CONTENT_PADDING_PX = 40
private const val ROW_SPACING_PX = 14f
private const val QR_SIZE_PX = 320
private const val BORDER_STROKE_PX = 4f
private const val DIVIDER_STROKE_PX = 2f

@Composable
actual fun rememberPrintShippingLabel(): (detail: PackageDetail) -> Unit {
    val context = LocalContext.current
    return { detail ->
        PrintHelper(context).apply {
            scaleMode = PrintHelper.SCALE_MODE_FIT
        }.printBitmap("Shipping label ${detail.trackingNumber}", buildShippingLabel(detail))
    }
}

/**
 * Draws a printable label bordered into three sections - a title, a "SHIP TO" recipient block,
 * and a QR code - matching a standard courier shipping label layout.
 */
private fun buildShippingLabel(detail: PackageDetail): Bitmap {
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
    val phone = detail.receiver.phone
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
        (addressLayout?.height?.toFloat() ?: 0f)
    val qrSectionHeight = CONTENT_PADDING_PX * 2 + QR_SIZE_PX + ROW_SPACING_PX + captionPaint.lineHeight()

    val totalHeight = (titleSectionHeight + shipToSectionHeight + qrSectionHeight).toInt()
    val bitmap = Bitmap.createBitmap(LABEL_WIDTH_PX, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

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
    if (addressLayout != null) {
        canvas.save()
        canvas.translate(CONTENT_PADDING_PX.toFloat(), y)
        addressLayout.draw(canvas)
        canvas.restore()
        y += addressLayout.height
    }
    y = titleSectionHeight + shipToSectionHeight
    canvas.drawLine(0f, y, LABEL_WIDTH_PX.toFloat(), y, linePaint)

    // QR section
    val qr = buildQrBitmap(detail.trackingNumber, QR_SIZE_PX)
    canvas.drawBitmap(qr, (LABEL_WIDTH_PX - QR_SIZE_PX) / 2f, y + CONTENT_PADDING_PX, null)
    canvas.drawText(
        detail.trackingNumber,
        LABEL_WIDTH_PX / 2f,
        y + CONTENT_PADDING_PX + QR_SIZE_PX + ROW_SPACING_PX - captionPaint.fontMetrics.ascent,
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

private fun buildQrBitmap(text: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
    return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).also { bitmap ->
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
    }
}
