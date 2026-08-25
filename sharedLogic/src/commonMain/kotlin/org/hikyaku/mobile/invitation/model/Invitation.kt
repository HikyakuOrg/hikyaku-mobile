package org.hikyaku.mobile.invitation.model

/** A team invitation awaiting the signed-in user's accept/decline decision. */
data class Invitation(
    val id: String,
    val organisationId: String,
    val organisationSlug: String,
    val organisationName: String,
    val role: String,
    val permissions: List<String>,
)
