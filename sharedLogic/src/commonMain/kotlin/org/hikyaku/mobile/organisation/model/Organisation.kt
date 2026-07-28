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
) {
    /** Personal organisations are the per-user default workspace. */
    val isPersonal: Boolean get() = orgType.equals("personal", ignoreCase = true)

    /** Human-friendly label, falling back when [name] is null. */
    val displayName: String
        get() = name ?: if (isPersonal) "Personal" else slug
}
