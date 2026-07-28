package org.hikyaku.mobile.packages

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The Material "Print" glyph, defined inline as an [ImageVector].
 *
 * Compose Multiplatform no longer ships the `material-icons-extended` artifact, so this icon is
 * declared here from the standard Material path data, following the same approach used for the
 * password visibility icons. It is tinted by the [androidx.compose.material3.Icon] that renders it.
 */
private const val PRINT_PATH =
    "M19 8H5c-1.66 0-3 1.34-3 3v6h4v4h12v-4h4v-6c0-1.66-1.34-3-3-3zm-3 11H8v-5h8v5zm3-7c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm-1-9H6v4h12V3z"

private var printIconCache: ImageVector? = null

/** Printer icon shown next to the "Print shipping label" action. */
val PrintIcon: ImageVector
    get() = printIconCache ?: ImageVector.Builder(
        name = "Print",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(PRINT_PATH),
        fill = SolidColor(Color.Black),
    ).build().also { printIconCache = it }
