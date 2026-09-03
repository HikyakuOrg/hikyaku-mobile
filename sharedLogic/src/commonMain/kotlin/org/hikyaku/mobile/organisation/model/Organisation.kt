package org.hikyaku.mobile.organisation.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row from the `organisations` table that the signed-in user can see. */
@Serializable
data class Organisation(
    val id: String,
    val name: String? = null,
    val slug: String,
    @SerialName("org_type") val orgType: String,
    @SerialName("created_by") val createdBy: String,
    /** The org's uploaded logo, as a public URL. Null until someone uploads one. */
    @SerialName("logo_url") val logoUrl: String? = null,
) {
    /** Personal organisations are the per-user default workspace. */
    val isPersonal: Boolean get() = orgType.equals("personal", ignoreCase = true)

    /** Company organisations are the shared workspaces that brand what their customers see. */
    val isCompany: Boolean get() = orgType.equals("company", ignoreCase = true)

    /**
     * The logo to brand this org's customer-facing artifacts with — currently the package QR code.
     * Null unless a company org has actually uploaded one, so callers can treat it as "brand this
     * if you can" without repeating the org-type check.
     */
    val brandingLogoUrl: String? get() = logoUrl?.takeIf { isCompany && it.isNotBlank() }

    /** Human-friendly label, falling back when [name] is null. */
    val displayName: String
        get() = name ?: if (isPersonal) "Personal" else slug
}
