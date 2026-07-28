package org.hikyaku.mobile.auth

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The Material "Visibility" / "VisibilityOff" glyphs, defined inline as [ImageVector]s.
 *
 * Compose Multiplatform no longer ships the `material-icons-extended` artifact, so the eye icons
 * used by the password field's show/hide toggle are declared here from the standard Material path
 * data. They are tinted by the [androidx.compose.material3.Icon] that renders them.
 */
private const val VISIBILITY_PATH =
    "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5z" +
        "M12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z" +
        "M12 9c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"

private const val VISIBILITY_OFF_PATH =
    "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5" +
        "-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7z" +
        "M2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27z" +
        "M7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2z" +
        "M11.84 9.02l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"

private fun buildIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
    ).build()

private var visibilityCache: ImageVector? = null
private var visibilityOffCache: ImageVector? = null

/** Eye icon shown when the password is currently masked (tap to reveal). */
val VisibilityIcon: ImageVector
    get() = visibilityCache ?: buildIcon("Visibility", VISIBILITY_PATH).also { visibilityCache = it }

/** Crossed-out eye icon shown when the password is currently visible (tap to hide). */
val VisibilityOffIcon: ImageVector
    get() = visibilityOffCache ?: buildIcon("VisibilityOff", VISIBILITY_OFF_PATH).also { visibilityOffCache = it }
