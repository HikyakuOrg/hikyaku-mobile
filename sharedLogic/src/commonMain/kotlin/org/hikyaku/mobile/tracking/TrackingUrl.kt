package org.hikyaku.mobile.tracking

import org.hikyaku.mobile.environment.model.EnvironmentSource

/**
 * The public tracking-page URL for [trackingNumber]: the org's [orgSlug] subdomain on the
 * canonical hosted instance, or [source]'s own domain when self-hosted.
 */
fun buildTrackingUrl(source: EnvironmentSource, orgSlug: String, trackingNumber: String): String {
    val base = when (source) {
        is EnvironmentSource.Default -> "https://$orgSlug.hikyaku.org"
        is EnvironmentSource.SelfHosted -> source.baseUrl.trimEnd('/')
    }
    return "$base/booking/tracking?reference=$trackingNumber"
}

/**
 * The tracking number carried by a scanned QR payload, or null if [raw] is blank. Package labels
 * encode the bare tracking number (see `PackageDetailScreen`'s `rememberQrKitPainter`), but a code
 * scanned off the public tracking page carries a full [buildTrackingUrl] — the inverse of that —
 * so a `reference=` query parameter is also accepted, as is any other URL's last path segment.
 */
fun parseScannedTrackingNumber(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    val referenceMatch = Regex("[?&]reference=([^&#]+)").find(trimmed)
    if (referenceMatch != null) {
        return referenceMatch.groupValues[1].trim().takeIf { it.isNotBlank() }?.uppercase()
    }

    val looksLikeUrl = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    if (looksLikeUrl) {
        val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
        val lastSegment = withoutQuery.trimEnd('/').substringAfterLast('/')
        return lastSegment.trim().takeIf { it.isNotBlank() }?.uppercase()
    }

    return trimmed.uppercase()
}
